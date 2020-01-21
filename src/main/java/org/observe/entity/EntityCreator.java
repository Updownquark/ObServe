package org.observe.entity;

public interface EntityCreator<E> extends EntityOperation<E> {
	/**
	 * Creates a new entity with this creator's configured field values
	 *
	 * @return The ID of the new entity
	 * @throws IllegalStateException If this creation has variables but has not been prepared or has unsatisfied variables
	 * @throws EntityOperationException If the creation fails
	 */
	EntityIdentity<E> create() throws IllegalStateException, EntityOperationException;

	/**
	 * Creates a new entity with this creator's configured field values
	 *
	 * @return The new entity
	 * @throws IllegalStateException If this creation has variables but has not been prepared or has unsatisfied variables
	 * @throws EntityOperationException If the creation fails
	 */
	ObservableEntity<E> createAndGet() throws IllegalStateException, EntityOperationException;
}
