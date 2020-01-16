package org.observe.entity;

public interface FieldConstraint<E, F> extends EntityConstraint<E> {
	ObservableEntityFieldType<E, F> getField();

	@Override
	default ObservableEntityType<E> getEntityType() {
		return getField().getEntityType();
	}
}
