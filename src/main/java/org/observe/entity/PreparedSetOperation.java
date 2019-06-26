package org.observe.entity;

public interface PreparedSetOperation<E> extends PreparedOperation<E>, EntitySetOperation<E> {
	@Override
	default PreparedSetOperation<E> prepare() throws IllegalStateException, EntityOperationException {
		return (PreparedSetOperation<E>) PreparedOperation.super.prepare();
	}
}
