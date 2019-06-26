package org.observe.entity;

public interface EntityDeletion<E> extends EntityModification<E> {
	@Override
	PreparedDeletion<E> prepare() throws IllegalStateException, EntityOperationException;
}
