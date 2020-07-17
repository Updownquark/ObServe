package org.observe.entity;

/**
 * The result of an {@link EntityQuery}'s {@link EntityQuery#collect(boolean) collect} method. The collection will be empty until this
 * result's {@link #getStatus() status} is {@link org.observe.config.ObservableOperationResult.ResultStatus#FULFILLED fulfilled}.
 *
 * @param <E> The type of entity in the result
 */
public interface EntityCollectionResult<E> extends EntityQueryResult<E> {
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
	 * @return The entities matching this query. Will be empty until
	 *         {@link org.observe.config.ObservableOperationResult.ResultStatus#FULFILLED fulfilled}
	 */
	ObservableEntitySet<E> get();
}
