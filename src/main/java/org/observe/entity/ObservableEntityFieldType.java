package org.observe.entity;

import com.google.common.reflect.TypeToken;

public interface ObservableEntityFieldType<E, T> {
	ObservableEntityType<E> getEntityType();
	TypeToken<T> getFieldType();
	String getName();

	ObservableEntityField<E, T> getField(ObservableEntity<? extends E> entity);
}
