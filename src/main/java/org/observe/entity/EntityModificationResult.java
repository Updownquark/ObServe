package org.observe.entity;

/**
 * The result of an {@link EntityUpdate} or {@link EntityDeletion}
 *
 * @param <E> The type of entity being updated or deleted
 */
public interface EntityModificationResult<E> extends ObservableEntityResult<E> {
	@Override
	EntityModification<E> getOperation();

	@Override
	default EntityModificationResult<E> waitFor() throws InterruptedException, EntityOperationException {
		ObservableEntityResult.super.waitFor();
		return this;
	}

	@Override
	default EntityModificationResult<E> waitFor(long timeout, int nanos) throws InterruptedException, EntityOperationException {
		ObservableEntityResult.super.waitFor(timeout, nanos);
		return this;
	}

	/**
	 * @return The number of entities that were affected (updated or deleted) by this operation, or -1 if this result's {@link #getStatus()
	 *         status} is not {@link org.observe.config.ObservableOperationResult.ResultStatus#FULFILLED fulfilled}
	 */
	long getModified();
}
