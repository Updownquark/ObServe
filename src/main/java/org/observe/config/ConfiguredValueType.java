package org.observe.config;

import java.util.function.Function;

import org.qommons.collect.QuickSet.QuickMap;

import com.google.common.reflect.TypeToken;

public interface ConfiguredValueType<E> {
	TypeToken<E> getType();

	QuickMap<String, ConfiguredValueField<? super E, ?>> getFields();

	int getFieldIndex(Function<? super E, ?> fieldGetter);

	boolean allowsCustomFields();
}
