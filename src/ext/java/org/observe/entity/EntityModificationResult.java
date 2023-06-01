package org.observe.entity;

/**
 * The result of an {@link EntityUpdate} or {@link EntityDeletion}
 *
 * @param <E> The type of entity being updated or deleted
 */
public interface EntityModificationResult<E> extends ObservableEntityResult<E, Long> {
	@Override
	EntityModification<E> getOperation();

	@Override
	default EntityModificationResult<E> waitFor() throws InterruptedException {
		ObservableEntityResult.super.waitFor();
		return this;
	}

	@Override
	default EntityModificationResult<E> waitFor(long timeout, int nanos) throws InterruptedException {
		ObservableEntityResult.super.waitFor(timeout, nanos);
		return this;
	}
}
