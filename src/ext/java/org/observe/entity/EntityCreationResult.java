package org.observe.entity;

import org.observe.config.ObservableCreationResult;

/**
 * The result of creating an entity with an {@link EntityCreator}
 *
 * @param <E> The type of entity being created
 */
public interface EntityCreationResult<E> extends ObservableEntityResult<E, E>, ObservableCreationResult<E> {
	@Override
	EntityCreator<? super E, E> getOperation();

	@Override
	default EntityCreationResult<E> waitFor() throws InterruptedException {
		ObservableEntityResult.super.waitFor();
		return this;
	}

	@Override
	default EntityCreationResult<E> waitFor(long timeout, int nanos) throws InterruptedException {
		ObservableEntityResult.super.waitFor(timeout, nanos);
		return this;
	}

	/**
	 * @return The entity created as a result of the operation, or null if this result's {@link #getStatus() status} is not
	 *         {@link org.observe.config.OperationResult.ResultStatus#FULFILLED fulfilled}
	 */
	ObservableEntity<? extends E> getNewEntity();

	@Override
	default E getResult() {
		ObservableEntity<? extends E> entity = getNewEntity();
		return entity == null ? null : entity.getEntity();
	}
}
