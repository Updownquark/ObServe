package org.observe.config;

import org.observe.collect.ObservableCollection;
import org.qommons.collect.CollectionElement;

import com.google.common.reflect.TypeToken;

public interface ObservableValueSet<E> {
	ConfiguredValueType<E> getType();

	ObservableCollection<? extends E> getValues();

	default ValueCreator<E, E> create() {
		return create(getType().getType());
	}

	<E2 extends E> ValueCreator<E, E2> create(TypeToken<E2> subType);

	<E2 extends E> CollectionElement<E> copy(E2 template);
}
