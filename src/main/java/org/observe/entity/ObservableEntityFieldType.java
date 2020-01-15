package org.observe.entity;

import com.google.common.reflect.TypeToken;

public interface ObservableEntityFieldType<E, T> extends EntityValueAccess<E, T> {
	ObservableEntityType<E> getEntityType();
	TypeToken<T> getFieldType();
	String getName();
	int getFieldIndex();

	ObservableEntityField<E, T> getField(ObservableEntity<? extends E> entity);

	@Override
	default TypeToken<T> getValueType() {
		return getFieldType();
	}

	@Override
	default T getValue(E entity) {
		return getField(getEntityType().observableEntity(entity)).get();
	}
}
