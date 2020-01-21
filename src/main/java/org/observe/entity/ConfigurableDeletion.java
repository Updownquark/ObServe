package org.observe.entity;

public interface ConfigurableDeletion<E> extends ConfigurableOperation<E>, EntityDeletion<E> {
	@Override
	PreparedDeletion<E> prepare() throws IllegalStateException, EntityOperationException;
}
