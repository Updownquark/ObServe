package org.observe.entity;

import org.observe.SettableValue;

import com.google.common.reflect.TypeToken;

public interface ObservableEntityField<E, T> extends SettableValue<T> {
	ObservableEntityFieldType<E, T> getFieldType();

	@Override
	default TypeToken<T> getType() {
		return getFieldType().getFieldType();
	}
}
