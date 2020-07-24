package org.observe.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.observe.Observable;
import org.observe.Observer;
import org.observe.SimpleObservable;
import org.observe.Subscription;
import org.qommons.Transaction;
import org.qommons.collect.ListenerList;

/**
 * An asynchronous result from an operation. Inspired by {@link Future}, it provides methods for inspecting, controlling, and listening too
 * the fulfillment state as the operation is executed in the entity service.
 *
 * @param <T> The type of the result
 */
public interface OperationResult<T> {
	/** An enum describing the current status of an {@link OperationResult} */
	enum ResultStatus {
		/** Fulfillment of the result has not yet begun */
		WAITING,
		/** Fulfillment of the result has begun, but not finished */
		EXECUTING,
		/** The result has been fulfilled and its contents are currently valid */
		FULFILLED,
		/** Fulfillment of the result was {@link OperationResult#cancel(boolean) cancelled} before fulfillment could finish */
		CANCELLED,
		/**
		 * Fulfillment of the result failed. In this case the {@link OperationResult#getFailure() getFailure()} method will return the
		 * reason.
		 */
		FAILED;

		public boolean isDone() {
			switch (this) {
			case WAITING:
			case EXECUTING:
				return false;
			default:
				return true;
			}
		}

		public boolean isAvailable() {
			return this == FULFILLED;
		}

		public boolean isFailed() {
			return this == FAILED;
		}
	}

	/**
	 * Attempts to cancel fulfillment of this result
	 *
	 * @param mayInterruptIfRunning {@code true} if the thread fulfilling this result should be interrupted; otherwise, in-progress results
	 *        are allowed to complete
	 * @return This operation
	 */
	OperationResult<T> cancel(boolean mayInterruptIfRunning);

	/** @return The current fulfillment status of this result */
	OperationResult.ResultStatus getStatus();

	/**
	 * Retrieves the result of the operation if it has been {@link ResultStatus#FULFILLED fulfilled}
	 *
	 * @return The result of the operation, or null if its status is not {@link ResultStatus#FULFILLED fulfilled}
	 * @see {@link #getStatus()}
	 */
	T getResult();

	/**
	 * @return An exception describing why fulfillment of this result {@link OperationResult.ResultStatus#FAILED}.
	 * @see #getStatus()
	 */
	ValueOperationException getFailure();

	/**
	 * @return The result of the operation, if it has been {@link ResultStatus#FULFILLED fulfilled}, or null if it has not finished or was
	 *         {@link ResultStatus#CANCELLED canceled}
	 * @throws ValueOperationException If the operation {@link OperationResult.ResultStatus#FAILED}
	 */
	default T getOrFail() throws ValueOperationException {
		switch (getStatus()) {
		case WAITING:
		case EXECUTING:
		case CANCELLED:
			return null;
		case FULFILLED:
			return getResult();
		case FAILED:
			ValueOperationException e = getFailure();
			if (e == null)
				e = new ValueOperationException("Operation failed without giving cause");
			throw e;
		}
		throw new IllegalStateException();
	}

	/**
	 * Waits if necessary for the computation to complete.
	 *
	 * @return This result
	 * @throws CancellationException If the computation was cancelled
	 * @throws InterruptedException If the current thread was interrupted while waiting
	 */
	default OperationResult<T> waitFor() throws InterruptedException {
		Subscription interruptSub = null;
		while (!getStatus().isDone()) {
			if (interruptSub == null)
				interruptSub = watchStatus().act(__ -> {
					if (getStatus().isDone()) {
						synchronized (this) {
							this.notify();
						}
					}
				});
			synchronized (this) {
				wait();
			}
		}
		if (interruptSub != null)
			interruptSub.unsubscribe();
		return this;
	}

	/**
	 * Waits if necessary for at most the given time for the computation to complete. If the result is
	 *
	 * @param timeout The maximum time to wait, in milliseconds
	 * @param nanos The nanoseconds to wait, in addition to the given milliseconds
	 * @return This result
	 * @throws CancellationException If the computation was cancelled
	 * @throws InterruptedException If the current thread was interrupted while waiting
	 */
	default OperationResult<T> waitFor(long timeout, int nanos) throws InterruptedException {
		Subscription interruptSub = null;
		while (!getStatus().isDone()) {
			if (interruptSub == null)
				interruptSub = watchStatus().act(__ -> {
					if (getStatus().isDone()) {
						synchronized (this) {
							this.notify();
						}
					}
				});
			synchronized (this) {
				wait(timeout, nanos);
			}
		}
		if (interruptSub != null)
			interruptSub.unsubscribe();
		return this;
	}

	/** @return An observable that fires (with this result as a value) when the result {@link #getStatus()} changes */
	Observable<? extends OperationResult<T>> watchStatus();

	/**
	 * @param onlyFulfilled Whether the listener should only fire if the operation succeeds. If false, the listener will be called for any
	 *        terminal result status.
	 * @param listener The listener to notify when this operation finishes
	 * @return The subscription to use to cancel the notification
	 */
	default Subscription whenDone(boolean onlyFulfilled, Consumer<? super OperationResult<T>> listener) {
		if (onlyFulfilled)
			return watchStatus().filter(r -> r.getStatus().isAvailable()).act(listener);
		else
			return watchStatus().filter(r -> r.getStatus().isDone()).act(listener);
	}

	/**
	 * Simple implementation of an operation result for a synchronous operation
	 *
	 * @param <T> The type of the result
	 */
	public class SynchronousResult<T> implements OperationResult<T> {
		private final T theValue;

		/** @param value The value of the result */
		public SynchronousResult(T value) {
			theValue = value;
		}

		@Override
		public OperationResult<T> cancel(boolean mayInterruptIfRunning) {
			return this;
		}

		@Override
		public OperationResult.ResultStatus getStatus() {
			return OperationResult.ResultStatus.FULFILLED;
		}

		@Override
		public T getResult() {
			return theValue;
		}

		@Override
		public ValueOperationException getFailure() {
			return null;
		}

		@Override
		public Observable<? extends OperationResult<T>> watchStatus() {
			return Observable.empty();
		}
	}

	/**
	 * Simple implementation of an operation result for an asynchronous operation
	 *
	 * @param <T> The type of the result
	 */
	public class AsyncResult<T> implements OperationResult<T> {
		private volatile ResultStatus theStatus;
		private final SimpleObservable<OperationResult<T>> theStatusChanges;
		private volatile boolean isCanceled;
		private volatile boolean isCanceledWithInterrupt;
		private volatile T theValue;
		private volatile ValueOperationException theFailure;

		/** Creates the result */
		public AsyncResult() {
			theStatus = ResultStatus.WAITING;
			theStatusChanges = SimpleObservable.build().safe(false).build(observer -> observer.onNext(this));
		}

		@Override
		public synchronized AsyncResult<T> cancel(boolean mayInterruptIfRunning) {
			isCanceled = true;
			if (mayInterruptIfRunning)
				isCanceledWithInterrupt = true;
			if (theStatus == ResultStatus.WAITING)
				updateStatus(ResultStatus.CANCELLED);
			return this;
		}

		@Override
		public ResultStatus getStatus() {
			return theStatus;
		}

		@Override
		public T getResult() {
			return theValue;
		}

		@Override
		public ValueOperationException getFailure() {
			return theFailure;
		}

		@Override
		public Observable<? extends OperationResult<T>> watchStatus() {
			return theStatusChanges.readOnly();
		}

		/**
		 * Called by the executor of the operation to begin work
		 *
		 * @return True if the executor should begin work, or false if this operation has already been canceled
		 */
		protected synchronized boolean begin() {
			return updateStatus(ResultStatus.EXECUTING);
		}

		/**
		 * Checks the canceled status of this result
		 *
		 * @param withInterrupt Whether the with-interrupt flag must be true to cancel (see the parameter for {@link #cancel(boolean)})
		 * @param changeStatusIfCanceled If true and this operation has been canceled, this call will change the status of the result to
		 *        {@link OperationResult.ResultStatus#CANCELLED}
		 * @return Whether this result has been canceled
		 */
		protected boolean checkCanceled(boolean withInterrupt, boolean changeStatusIfCanceled) {
			if (!changeStatusIfCanceled)
				return withInterrupt ? isCanceledWithInterrupt : isCanceled;
			synchronized (this) {
				boolean canceled = withInterrupt ? isCanceledWithInterrupt : isCanceled;
				if (canceled)
					updateStatus(ResultStatus.CANCELLED);
				return canceled;
			}
		}

		/**
		 * Marks this result as {@link OperationResult.ResultStatus#FULFILLED}
		 *
		 * @param value The value result of the operation
		 */
		protected synchronized void fulfilled(T value) {
			theValue = value;
			updateStatus(ResultStatus.FULFILLED);
		}

		/**
		 * Marks this result as {@link OperationResult.ResultStatus#FAILED}
		 *
		 * @param failure The exception that caused or represents the failure
		 */
		protected synchronized void failed(ValueOperationException failure) {
			theFailure = failure;
			updateStatus(ResultStatus.FAILED);
		}

		private boolean updateStatus(ResultStatus newStatus) {
			if (newStatus.compareTo(theStatus) <= 0)
				return false;
			theStatus = newStatus;
			theStatusChanges.onNext(this);
			if (newStatus.isDone())
				theStatusChanges.onCompleted(this);
			return true;
		}
	}

	/**
	 * A result implementation that is powered by a wrapped result to be installed later
	 *
	 * @param <S> The type of the wrapped result
	 * @param <T> The type of the result
	 */
	public abstract class WrapperResult<S, T> implements OperationResult<T> {
		private volatile OperationResult<? extends S> theWrapped;
		private volatile boolean isCanceled;
		private volatile boolean isCanceledWithInterrupt;
		private ListenerList<Observer<? super OperationResult<T>>> theObservers;

		/** @param wrapped The result to power this result */
		protected synchronized void setWrapped(OperationResult<? extends S> wrapped) {
			theWrapped = wrapped;
			if (isCanceled)
				wrapped.cancel(isCanceledWithInterrupt);
			ListenerList<Observer<? super OperationResult<T>>> observers = theObservers;
			if (observers != null) {
				theObservers = null;
				boolean withInit = wrapped.getStatus() != ResultStatus.WAITING;
				Observable<? extends OperationResult<T>> statusObservable = (withInit ? theWrapped.watchStatus()
					: theWrapped.watchStatus().noInit()).map(__ -> WrapperResult.this);
				observers.forEach(listener -> statusObservable.subscribe(listener));
			}
			notifyAll(); // Wake up threads waiting for the wrapped (e.g. watchStatus().lock())
		}

		@Override
		public synchronized WrapperResult<S, T> cancel(boolean mayInterruptIfRunning) {
			if (theWrapped != null)
				theWrapped.cancel(mayInterruptIfRunning);
			isCanceled = true;
			if (mayInterruptIfRunning)
				isCanceledWithInterrupt = true;
			return this;
		}

		@Override
		public ResultStatus getStatus() {
			OperationResult<? extends S> wrapped = theWrapped;
			return wrapped == null ? ResultStatus.WAITING : wrapped.getStatus();
		}

		@Override
		public T getResult() {
			OperationResult<? extends S> wrapped = theWrapped;
			if (wrapped == null)
				return null;
			switch (wrapped.getStatus()) {
			case WAITING:
			case EXECUTING:
			case CANCELLED:
			case FAILED:
				return null;
			case FULFILLED:
				return wrapped == null ? null : getResult(wrapped);
			}
			throw new IllegalStateException();
		}

		/**
		 * @param wrapped The fulfilled wrapped result
		 * @return The value for this result
		 */
		protected abstract T getResult(OperationResult<? extends S> wrapped);

		@Override
		public ValueOperationException getFailure() {
			OperationResult<? extends S> wrapped = theWrapped;
			return wrapped == null ? null : wrapped.getFailure();
		}

		@Override
		public Observable<? extends OperationResult<T>> watchStatus() {
			return new Observable<OperationResult<T>>() {
				@Override
				public Object getIdentity() {
					return WrapperResult.this;
				}

				@Override
				public Subscription subscribe(Observer<? super OperationResult<T>> observer) {
					synchronized (WrapperResult.this) {
						OperationResult<? extends S> wrapped = theWrapped;
						if (wrapped != null)
							wrapped.watchStatus().map(__ -> WrapperResult.this).subscribe(observer);
						if (theObservers == null)
							theObservers = ListenerList.build().build();
						return theObservers.add(observer, false)::run;
					}
				}

				@Override
				public boolean isSafe() {
					return true;
				}

				@Override
				public Transaction lock() {
					synchronized (WrapperResult.this) {
						OperationResult<? extends S> wrapped = theWrapped;
						while (wrapped == null) {
							try {
								WrapperResult.this.wait();
							} catch (InterruptedException e) {}
							wrapped = theWrapped;
						}
						return wrapped.watchStatus().lock();
					}
				}

				@Override
				public Transaction tryLock() {
					OperationResult<? extends S> wrapped = theWrapped;
					return wrapped == null ? null : wrapped.watchStatus().tryLock();
				}
			};
		}
	}

	/**
	 * A result composed of multiple component results
	 *
	 * @param <S> The type of the component results
	 * @param <T> The type of this result
	 */
	public abstract class MultiResult<S, T> implements OperationResult<T> {
		private final Collection<? extends OperationResult<? extends S>> theComponents;

		/** @param components The components for this result */
		public MultiResult(Collection<? extends OperationResult<? extends S>> components) {
			theComponents = components;
		}

		@Override
		public OperationResult<T> cancel(boolean mayInterruptIfRunning) {
			for (OperationResult<? extends S> component : theComponents)
				component.cancel(mayInterruptIfRunning);
			return this;
		}

		@Override
		public ResultStatus getStatus() {
			boolean hasDone = false, hasWaiting = false, hasCanceled = false, hasFailed = false;
			componentLoop: for (OperationResult<? extends S> component : theComponents) {
				switch (component.getStatus()) {
				case WAITING:
					hasWaiting = true;
					if (hasDone)
						break componentLoop;
					break;
				case EXECUTING:
					return ResultStatus.EXECUTING;
				case CANCELLED:
					hasCanceled = true;
					break;
				case FAILED:
					hasFailed = true;
					break;
				case FULFILLED:
					hasDone = true;
					break;
				}
			}
			if (hasWaiting)
				return (hasDone || hasFailed) ? ResultStatus.EXECUTING : ResultStatus.WAITING;
			else if (hasFailed)
				return ResultStatus.FAILED;
			if (hasCanceled)
				return ResultStatus.CANCELLED;
			else
				return ResultStatus.FULFILLED;
		}

		@Override
		public T getResult() {
			if (getStatus().isAvailable())
				return getResult(theComponents.stream().map(OperationResult::getResult)//
					.collect(Collectors.toCollection(() -> new ArrayList<>(theComponents.size()))));
			return null;
		}

		/**
		 * @param componentResults The list of the results of all component results
		 * @return The result value for this result
		 */
		protected abstract T getResult(List<S> componentResults);

		@Override
		public ValueOperationException getFailure() {
			for (OperationResult<? extends S> component : theComponents) {
				ValueOperationException failure = component.getFailure();
				if (failure != null)
					return failure;
			}
			return null;
		}

		@Override
		public Observable<? extends OperationResult<T>> watchStatus() {
			List<Observable<?>> components = new ArrayList<>(theComponents.size());
			Iterator<? extends OperationResult<? extends S>> componentIter = theComponents.iterator();
			if (!componentIter.hasNext())
				return Observable.constant(this);
			while (true) {
				OperationResult<? extends S> component = componentIter.next();
				if (componentIter.hasNext())
					components.add(component.watchStatus().noInit());
				else {
					components.add(component.watchStatus()); // Add the last component with initialization
					break;
				}
			}
			AtomicReference<ResultStatus> status = new AtomicReference<>(getStatus());
			return Observable.or(components.toArray(new Observable[components.size()])).filter(r -> {
				ResultStatus newStatus = getStatus();
				return status.getAndSet(newStatus) != newStatus;
			}).map(__ -> this);
		}

		@Override
		public OperationResult<T> waitFor() throws InterruptedException {
			for (OperationResult<? extends S> component : theComponents) {
				component.waitFor();
			}
			return this;
		}

		@Override
		public OperationResult<T> waitFor(long timeout, int nanos) throws InterruptedException {
			long now = System.currentTimeMillis();
			int nowNanos;
			long targetMillis = now + timeout;
			long targetNanos;
			if (nanos == 0) {
				nowNanos = 0;
				targetNanos = 0;
			} else {
				nowNanos = (int) (System.nanoTime() % 1_000_000);
				targetMillis += nanos / 1_000_000;
				targetNanos = nowNanos + nanos % 1_000_000;
			}
			for (OperationResult<? extends S> component : theComponents) {
				component.waitFor(timeout, nowNanos);
				now = System.currentTimeMillis();
				nowNanos = nanos == 0 ? 0 : (int) (System.nanoTime() % 1_000_000);
				if (now > targetMillis)
					break;
				else if (now == targetMillis) {
					if (nanos == 0 || nowNanos >= targetNanos)
						break;
				}
			}
			return this;
		}

		@Override
		public Subscription whenDone(boolean onlyFulfilled, Consumer<? super OperationResult<T>> listener) {
			// TODO Auto-generated method stub
			return OperationResult.super.whenDone(onlyFulfilled, listener);
		}
	}
}
