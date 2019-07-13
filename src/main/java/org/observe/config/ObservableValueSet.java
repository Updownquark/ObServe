package org.observe.config;

import org.observe.collect.ObservableCollection;

public interface ObservableValueSet<E> {
	ObservableCollection<? extends E> getValues();

	ValueCreator<E> create();
}
