package org.observe.entity;

public interface PreparedQuery<E> extends PreparedSetOperation<E>, EntityQuery<E> {
	@Override
	default PreparedQuery<E> prepare() throws IllegalStateException, EntityOperationException {
		return (PreparedQuery<E>) PreparedSetOperation.super.prepare();
	}
}
