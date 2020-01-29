package org.observe.entity;

/**
 * A entity operation that may change the contents of existing entities an entity set
 *
 * @param <E> The type of entity to modify
 */
public interface EntityModification<E> extends EntitySetOperation<E> {
	/**
	 * Performs this operation
	 *
	 * @param sync Whether to execute the operation synchronously (blocking until completion or failure) or asynchronously
	 * @param cause The cause of the change, if any
	 * @return The result for the number of entities affected (deleted or updated) by the operation
	 * @throws IllegalStateException If this operation has variables but has not been prepared or has unsatisfied variables
	 * @throws EntityOperationException If the operation fails immediately
	 */
	EntityModificationResult<E> execute(boolean sync, Object cause) throws IllegalStateException, EntityOperationException;
}
