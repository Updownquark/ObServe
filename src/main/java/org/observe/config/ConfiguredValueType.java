package org.observe.config;

import java.util.function.Function;

import org.qommons.collect.QuickSet.QuickMap;

import com.google.common.reflect.TypeToken;

public interface ConfiguredValueType<E> {
	TypeToken<E> getType();

	QuickMap<String, ConfiguredValueField<? super E, ?>> getFields();

	<F> ConfiguredValueField<? super E, F> getField(Function<? super E, F> fieldGetter);

	boolean allowsCustomFields();
}
