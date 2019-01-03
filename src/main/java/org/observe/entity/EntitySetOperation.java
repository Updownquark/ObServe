package org.observe.entity;

public interface EntitySetOperation<E> extends EntityOperation<E> {
	EntitySelection<E> getSelection();

	@Override
	EntitySetOperation<E> prepare() throws IllegalStateException, EntityOperationException;

	@Override
	EntitySetOperation<E> satisfy(String variableName, Object value) throws IllegalStateException, IllegalArgumentException;

	/**
	 * Performs this operation
	 *
	 * @return The number of entities affected by the operation
	 * @throws IllegalStateException If this operation has variables but has not been prepared or has unsatisfied variables
	 * @throws EntityOperationException If the operation fails
	 */
	long execute() throws IllegalStateException, EntityOperationException;
}
