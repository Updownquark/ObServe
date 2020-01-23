package org.observe.entity;

/**
 * An un-prepared {@link EntityDeletion}
 * 
 * @param <E> The type of entity to delete
 */
public interface ConfigurableDeletion<E> extends ConfigurableOperation<E>, EntityDeletion<E> {
	@Override
	PreparedDeletion<E> prepare() throws IllegalStateException, EntityOperationException;
}
