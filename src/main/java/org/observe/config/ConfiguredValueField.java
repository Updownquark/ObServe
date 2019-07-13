package org.observe.config;

import com.google.common.reflect.TypeToken;

public interface ConfiguredValueField<E, T> {
	ConfiguredValueType<E> getValueType();

	String getName();

	TypeToken<T> getFieldType();

	int getIndex();
}
