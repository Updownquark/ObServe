package org.observe.entity;

/**
 * The result of an {@link EntityQuery}'s {@link EntityQuery#collect(boolean) collect} method. The collection will be empty until this
 * result's {@link #getStatus() status} is {@link org.observe.config.OperationResult.ResultStatus#FULFILLED fulfilled}.
 *
 * @param <E> The type of entity in the result
 */
public interface EntityCollectionResult<E> extends EntityQueryResult<E, ObservableEntitySet<E>> {
	@Override
	default EntityCollectionResult<E> waitFor() throws InterruptedException, EntityOperationException {
		EntityQueryResult.super.waitFor();
		return this;
	}

	@Override
	default EntityCollectionResult<E> waitFor(long timeout, int nanos) throws InterruptedException, EntityOperationException {
		EntityQueryResult.super.waitFor(timeout, nanos);
		return this;
	}

	/**
	 * @return The entities matching the query, or an empty collection if the result is not
	 *         {@link org.observe.config.OperationResult.ResultStatus#FULFILLED fulfilled}
	 */
	@Override
	ObservableEntitySet<E> getResult();
}
