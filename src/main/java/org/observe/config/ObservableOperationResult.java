package org.observe.config;

import java.util.concurrent.CancellationException;

import org.observe.Observable;

/**
 * An asynchronous result from an operation on a set of observable values, e.g. a
 * {@link ConfigurableValueCreator#createAsync(java.util.function.Consumer) value creation} operation
 *
 * @param <E> The type of the entity/value that the operation was against
 * @param <T> The type of the result
 */
public interface ObservableOperationResult<E, T> extends OperationResult<T> {
	/** @return The value type that this result is for */
	ConfiguredValueType<E> getValueType();

	/**
	 * Waits if necessary for the computation to complete.
	 *
	 * @return This result
	 * @throws CancellationException If the computation was cancelled
	 * @throws ValueOperationException If the operation has failed or fails while waiting
	 * @throws InterruptedException If the current thread was interrupted while waiting
	 */
	@Override
	default ObservableOperationResult<E, T> waitFor() throws InterruptedException, ValueOperationException {
		OperationResult.super.waitFor();
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
	@Override
	default ObservableOperationResult<E, T> waitFor(long timeout, int nanos) throws InterruptedException, ValueOperationException {
		OperationResult.super.waitFor(timeout, nanos);
		return this;
	}

	/** @return An observable that fires (with this result as a value) when the result {@link #getStatus()} changes */
	@Override
	Observable<? extends ObservableOperationResult<E, T>> watchStatus();

	/**
	 * Abstract implementation of an operation result for a synchronous operation
	 *
	 * @param <E> The type of the entity/value that the operation was against
	 * @param <T> The type of the result
	 */
	public class SynchronousResult<E, T> extends OperationResult.SynchronousResult<T> implements ObservableOperationResult<E, T> {
		private final ConfiguredValueType<E> theType;

		/**
		 * @param type The entity/value type that the operation was against
		 * @param value The result of the operation
		 */
		public SynchronousResult(ConfiguredValueType<E> type, T value) {
			super(value);
			theType = type;
		}

		@Override
		public ConfiguredValueType<E> getValueType() {
			return theType;
		}

		@Override
		public Observable<? extends ObservableOperationResult<E, T>> watchStatus() {
			return Observable.empty();
		}
	}
}
