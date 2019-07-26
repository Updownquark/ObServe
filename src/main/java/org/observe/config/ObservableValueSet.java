package org.observe.config;

import org.observe.collect.ObservableCollection;
import org.qommons.collect.ElementId;

public interface ObservableValueSet<E> {
	ConfiguredValueType<E> getType();

	ObservableCollection<? extends E> getValues();

	default ValueCreator<E> create() {
		return create(null, null, false);
	}

	ValueCreator<E> create(ElementId after, ElementId before, boolean first);
}
