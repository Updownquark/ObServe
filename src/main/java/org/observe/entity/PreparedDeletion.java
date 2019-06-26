package org.observe.entity;

public interface PreparedDeletion<E> extends PreparedSetOperation<E>, EntityDeletion<E> {
	@Override
	default PreparedDeletion<E> prepare() throws IllegalStateException, EntityOperationException {
		return (PreparedDeletion<E>) PreparedSetOperation.super.prepare();
	}
}
