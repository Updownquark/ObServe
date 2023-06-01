package org.observe.entity;

/**
 * An un-prepared operation
 *
 * @param <E> The type of entity to operate on
 */
public interface ConfigurableOperation<E> extends EntityOperation<E> {
	/**
	 * Creates a prepared operation with the same information as this operation. A prepared operation may be much faster to execute.
	 * 
	 * @return A prepared operation configured like this operation
	 * @throws IllegalStateException
	 * @throws EntityOperationException
	 */
	PreparedOperation<E> prepare() throws IllegalStateException, EntityOperationException;
}
