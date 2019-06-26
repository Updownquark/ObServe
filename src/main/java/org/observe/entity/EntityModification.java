package org.observe.entity;

public interface EntityModification<E> extends EntitySetOperation<E> {
	/**
	 * Performs this operation
	 *
	 * @return The number of entities affected by the operation
	 * @throws IllegalStateException If this operation has variables but has not been prepared or has unsatisfied variables
	 * @throws EntityOperationException If the operation fails
	 */
	long execute() throws IllegalStateException, EntityOperationException;
}
