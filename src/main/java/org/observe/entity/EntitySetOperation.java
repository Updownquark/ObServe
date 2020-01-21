package org.observe.entity;

public interface EntitySetOperation<E> extends EntityOperation<E> {
	EntitySelection<E> getSelection();

	@Override
	default ObservableEntityType<E> getEntityType() {
		return getSelection().getEntityType();
	}
}
