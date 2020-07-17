package org.observe.config;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

import org.observe.Observable;
import org.observe.Subscription;

/**
 * An asynchronous result from an operation on a set of observable values, e.g. a
 * {@link ConfigurableValueCreator#createAsync(java.util.function.Consumer) value creation} operation. Inspired by {@link Future}, it provides methods
 * for inspecting, controlling, and listening too the fulfillment state as the operation is executed in the entity service.
 *
 * @param <E> The type of the entity/value that the operation was against
 */
public interface ObservableOperationResult<E> {
	/** An enum describing the current status of an {@link ObservableOperationResult} */
	enum ResultStatus {
		/** Fulfillment of the result has not yet begun */
		WAITING,
		/** Fulfillment of the result has begun, but not finished */
		EXECUTING,
		/** The result has been fulfilled and its contents are currently valid */
		FULFILLED,
		/** Fulfillment of the result was {@link ObservableOperationResult#cancel(boolean) cancelled} before fulfillment could finish */
		CANCELLED,
		/**
		 * Fulfillment of the result failed. In this case the {@link ObservableOperationResult#getFailure() getFailure()} method will return
		 * the reason.
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
	}

	/** @return The value type that this result is for */
	ConfiguredValueType<E> getValueType();

	/**
	 * Attempts to cancel fulfillment of this result
	 *
	 * @param mayInterruptIfRunning {@code true} if the thread fulfilling this result should be interrupted; otherwise, in-progress results
	 *        are allowed to complete
	 */
	void cancel(boolean mayInterruptIfRunning);

	/** @return The current fulfillment status of this result */
	ResultStatus getStatus();

	/**
	 * @return An exception describing why fulfillment of this result {@link ObservableOperationResult.ResultStatus#FAILED}.
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
	default ObservableOperationResult<E> waitFor() throws InterruptedException, ValueOperationException {
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
	default ObservableOperationResult<E> waitFor(long timeout, int nanos) throws InterruptedException, ValueOperationException {
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
	Observable<? extends ObservableOperationResult<E>> watchStatus();

	/**
	 * Abstract implementation of an operation result for a synchronous operation
	 *
	 * @param <E> The type of the value
	 */
	abstract class SimpleResult<E> implements ObservableOperationResult<E> {
		private final ConfiguredValueType<E> theType;

		public SimpleResult(ConfiguredValueType<E> type) {
			theType = type;
		}

		@Override
		public ConfiguredValueType<E> getValueType() {
			return theType;
		}

		@Override
		public void cancel(boolean mayInterruptIfRunning) {}

		@Override
		public ResultStatus getStatus() {
			return ResultStatus.FULFILLED;
		}

		@Override
		public ValueOperationException getFailure() {
			return null;
		}

		@Override
		public Observable<? extends ObservableOperationResult<E>> watchStatus() {
			return Observable.empty();
		}
	}
}
