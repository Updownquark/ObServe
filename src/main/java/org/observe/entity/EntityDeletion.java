package org.observe.entity;

public interface EntityDeletion<E> extends EntityModification<E> {
	@Override
	EntityDeletion<E> prepare() throws IllegalStateException, EntityOperationException;

	@Override
	EntityDeletion<E> satisfy(String variableName, Object value) throws IllegalStateException, IllegalArgumentException;
}
