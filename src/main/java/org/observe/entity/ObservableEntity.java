package org.observe.entity;

public interface ObservableEntity<E> {
	ObservableEntityType<E> getType();

	EntityIdentity<? super E> getId();

	<F> ObservableEntityField<? super E, F> getField(ObservableEntityFieldType<? super E, F> fieldType);

	E getEntity();
}
