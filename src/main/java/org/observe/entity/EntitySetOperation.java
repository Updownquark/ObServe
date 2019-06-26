package org.observe.entity;

public interface EntitySetOperation<E> extends EntityOperation<E> {
	EntitySelection<E> getSelection();

	@Override
	PreparedSetOperation<E> prepare() throws IllegalStateException, EntityOperationException;
}
