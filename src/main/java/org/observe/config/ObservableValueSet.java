package org.observe.config;

import org.observe.collect.ObservableCollection;

public interface ObservableValueSet<E> {
	ConfiguredValueType<E> getType();

	ObservableCollection<? extends E> getValues();

	ValueCreator<E> create();
}
