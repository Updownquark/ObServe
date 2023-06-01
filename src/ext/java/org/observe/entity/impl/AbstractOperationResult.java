package org.observe.entity.impl;

import org.observe.Observable;
import org.observe.config.OperationResult;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQueryResult;
import org.observe.entity.ObservableEntityResult;

/**
 * Abstract {@link ObservableEntityResult} implementation that handles status changes and {@link EntityQueryResult#cancel(boolean)
 * cancellation}
 *
 * @param <E> The entity type of the operation
 * @param <S> The type of the wrapped result
 * @param <T> The type of the result
 */
public abstract class AbstractOperationResult<E, S, T> extends OperationResult.WrapperResult<S, T> implements ObservableEntityResult<E, T> {
	@Override
	protected synchronized void setWrapped(OperationResult<? extends S> wrapped) {
		super.setWrapped(wrapped);
	}

	@Override
	public EntityOperationException getFailure() {
		return (EntityOperationException) super.getFailure();
	}

	@Override
	public Observable<? extends ObservableEntityResult<E, T>> watchStatus() {
		return (Observable<? extends ObservableEntityResult<E, T>>) super.watchStatus();
	}
}
