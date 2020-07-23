package org.observe.config;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

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
	 */
	void cancel(boolean mayInterruptIfRunning);

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
	 * Waits if necessary for the computation to complete.
	 *
	 * @return This result
	 * @throws CancellationException If the computation was cancelled
	 * @throws ValueOperationException If the operation has failed or fails while waiting
	 * @throws InterruptedException If the current thread was interrupted while waiting
	 */
	default OperationResult<T> waitFor() throws InterruptedException, ValueOperationException {
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
		ValueOperationException failure = getFailure();
		if (failure != null)
			throw failure;
		return this;
	}

	/**
	 * Waits if necessary for at most the given time for the computation to complete. If the result is
	 *
	 * @param timeout The maximum time to wait, in milliseconds
	 * @param nanos The nanoseconds to wait, in addition to the given milliseconds
	 * @return This result
	 * @throws CancellationException If the computation was cancelled
	 * @throws ValueOperationException If the operation has failed or fails while waiting
	 * @throws InterruptedException If the current thread was interrupted while waiting
	 */
	default OperationResult<T> waitFor(long timeout, int nanos) throws InterruptedException, ValueOperationException {
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
		ValueOperationException failure = getFailure();
		if (failure != null)
			throw failure;
		return this;
	}

	/** @return An observable that fires (with this result as a value) when the result {@link #getStatus()} changes */
	Observable<? extends OperationResult<T>> watchStatus();

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
		public void cancel(boolean mayInterruptIfRunning) {}

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
		public synchronized void cancel(boolean mayInterruptIfRunning) {
			isCanceled = true;
			if (mayInterruptIfRunning)
				isCanceledWithInterrupt = true;
			if (theStatus == ResultStatus.WAITING)
				updateStatus(ResultStatus.CANCELLED);
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
		public synchronized void cancel(boolean mayInterruptIfRunning) {
			if (theWrapped != null)
				theWrapped.cancel(mayInterruptIfRunning);
			isCanceled = true;
			if (mayInterruptIfRunning)
				isCanceledWithInterrupt = true;
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
}
