package org.observe.entity;

public interface ConfigurableOperation<E> extends EntityOperation<E> {
	PreparedOperation<E> prepare() throws IllegalStateException, EntityOperationException;
}
