package org.observe.config;

import org.qommons.collect.QuickSet.QuickMap;

public interface ConfiguredValueType<E> {
	QuickMap<String, ConfiguredValueField<? super E, ?>> getFields();
}
