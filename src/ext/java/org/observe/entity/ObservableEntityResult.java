package org.observe.entity;

import java.util.concurrent.Future;

import org.observe.Observable;
import org.observe.config.ObservableOperationResult;

/**
 * All results of entity operations return a sub-type of this interface. Inspired by {@link Future}, it provides methods for inspecting,
 * controlling, and listening too the fulfillment state as the operation is executed in the entity service.
 *
 * @param <E> The type of the entity that the operation was against
 * @param <T> The type of the result
 */
public interface ObservableEntityResult<E, T> extends ObservableOperationResult<E, T> {
	/** @return The operation that this result is for */
	EntityOperation<E> getOperation();

	@Override
	default ObservableEntityType<E> getValueType() {
		return getOperation().getEntityType();
	}

	@Override
	EntityOperationException getFailure();

	@Override
	default ObservableEntityResult<E, T> waitFor() throws InterruptedException {
		ObservableOperationResult.super.waitFor();
		return this;
	}

	@Override
	default ObservableEntityResult<E, T> waitFor(long timeout, int nanos) throws InterruptedException {
		ObservableOperationResult.super.waitFor(timeout, nanos);
		return this;
	}

	@Override
	Observable<? extends ObservableEntityResult<E, T>> watchStatus();
}
