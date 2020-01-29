package org.observe.entity;

/**
 * The result of creating an entity with an {@link EntityCreator}
 *
 * @param <E> The type of entity being created
 */
public interface EntityCreationResult<E> extends ObservableEntityResult<E> {
	@Override
	EntityCreator<E> getOperation();

	@Override
	default EntityCreationResult<E> waitFor() throws InterruptedException, EntityOperationException {
		ObservableEntityResult.super.waitFor();
		return this;
	}

	@Override
	default EntityCreationResult<E> waitFor(long timeout, int nanos) throws InterruptedException, EntityOperationException {
		ObservableEntityResult.super.waitFor(timeout, nanos);
		return this;
	}

	/**
	 * @return The entity created as a result of the operation, or null if this result's {@link #getStatus() status} is not
	 *         {@link ObservableEntityResult.ResultStatus#FULFILLED fulfilled}
	 */
	ObservableEntity<? extends E> getNewEntity();
}
