package org.observe.entity;

public interface PreparedUpdate<E> extends PreparedSetOperation<E>, EntityUpdate<E> {
	@Override
	default PreparedUpdate<E> prepare() throws IllegalStateException, EntityOperationException {
		return (PreparedUpdate<E>) PreparedSetOperation.super.prepare();
	}
}
