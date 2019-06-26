package org.observe.entity;

public interface PreparedCreator<E> extends PreparedOperation<E>, EntityCreator<E> {
	@Override
	default PreparedCreator<E> prepare() throws IllegalStateException, EntityOperationException {
		return (PreparedCreator<E>) PreparedOperation.super.prepare();
	}
}
