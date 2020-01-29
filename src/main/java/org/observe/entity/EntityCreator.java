package org.observe.entity;

/**
 * An operation to create a new instance of an entity type
 *
 * @param <E> The entity type to create an instance for
 */
public interface EntityCreator<E> extends EntityOperation<E> {
	/**
	 * Creates a new entity with this creator's configured field values
	 *
	 * @param sync Whether to execute the operation synchronously (blocking until completion or failure) or asynchronously
	 * @param cause The cause of the change, if any
	 * @return The result containing new entity
	 * @throws IllegalStateException If this creation has variables but has not been prepared or has unsatisfied variables
	 * @throws EntityOperationException If the creation fails immediately
	 */
	EntityCreationResult<E> create(boolean sync, Object cause) throws IllegalStateException, EntityOperationException;
}
