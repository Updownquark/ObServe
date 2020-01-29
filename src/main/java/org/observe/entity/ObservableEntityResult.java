package org.observe.entity;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.observe.Subscription;

/**
 * All results of entity operations return a sub-type of this interface. Inspired by {@link Future}, it provides methods for inspecting,
 * controlling, and listening too the fulfillment state as the operation is executed in the entity service.
 *
 * @param <E> The type of the entity that the operation was against
 */
public interface ObservableEntityResult<E> {
	/** An enum describing the current status of an {@link ObservableEntityResult} */
	enum ResultStatus {
		/** Fulfillment of the result has not yet begun */
		WAITING,
		/** Fulfillment of the result has begun, but not finished */
		EXECUTING,
		/** The result has been fulfilled and its contents are currently valid */
		FULFILLED,
		/** Fulfillment of the result was {@link ObservableEntityResult#cancel(boolean) cancelled} before fulfillment could finish */
		CANCELLED,
		/**
		 * Fulfillment of the result failed. In this case the {@link ObservableEntityResult#getFailure() getFailure()} method will return
		 * the reason.
		 */
		FAILED,
		/** The result was fulfilled, but then */
		DISPOSED;

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

	/** @return The operation that this result is for */
	EntityOperation<E> getOperation();

	/**
	 * Attempts to cancel fulfillment of this result or, if this result is {@link EntityQueryResult updating},
	 * {@link EntityQueryResult#dispose() disposes} it.
	 *
	 * @param mayInterruptIfRunning {@code true} if the thread fulfilling this result should be interrupted; otherwise, in-progress results
	 *        are allowed to complete
	 * @return {@code false} if the result was already fulfilled, or has failed or been previously cancelled; {@code true} otherwise
	 */
	boolean cancel(boolean mayInterruptIfRunning);

	/** @return The current fulfillment status of this result */
	ResultStatus getStatus();

	/**
	 * @return An exception describing why fulfillment of this result {@link ObservableEntityResult.ResultStatus#FAILED}.
	 * @see #getStatus()
	 */
	EntityOperationException getFailure();

	/**
	 * Waits if necessary for the computation to complete.
	 *
	 * @return This result
	 * @throws CancellationException If the computation was cancelled
	 * @throws EntityOperationException If the operation has failed or fails while waiting
	 * @throws InterruptedException If the current thread was interrupted while waiting
	 */
	default ObservableEntityResult<E> waitFor() throws InterruptedException, EntityOperationException {
		Subscription interruptSub = null;
		while (!getStatus().isDone()) {
			if (interruptSub == null)
				interruptSub = onStatusChange(__ -> this.notify());
			synchronized (this) {
				wait();
			}
		}
		if (interruptSub != null)
			interruptSub.unsubscribe();
		EntityOperationException failure = getFailure();
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
	 * @throws EntityOperationException If the operation has failed or fails while waiting
	 * @throws InterruptedException If the current thread was interrupted while waiting
	 */
	default ObservableEntityResult<E> waitFor(long timeout, int nanos) throws InterruptedException, EntityOperationException {
		Subscription interruptSub = null;
		while (!getStatus().isDone()) {
			if (interruptSub == null)
				interruptSub = onStatusChange(__ -> this.notify());
			synchronized (this) {
				wait(timeout, nanos);
			}
		}
		if (interruptSub != null)
			interruptSub.unsubscribe();
		EntityOperationException failure = getFailure();
		if (failure != null)
			throw failure;
		return this;
	}

	/**
	 * @param onChange A listener to be notified whenever this result's {@link #getStatus() status} changes
	 * @return A subscription to cease status notification for the listener
	 */
	Subscription onStatusChange(Consumer<ObservableEntityResult<E>> onChange);
}
