package org.observe.config;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.qommons.collect.CollectionElement;

public interface ValueCreator<E> {
	ConfiguredValueType<E> getType();

	Set<Integer> getRequiredFields();

	ValueCreator<E> with(String fieldName, Object value) throws IllegalArgumentException;

	<F> ValueCreator<E> with(ConfiguredValueField<? super E, F> field, F value) throws IllegalArgumentException;

	<F> ValueCreator<E> with(Function<? super E, F> fieldGetter, F value) throws IllegalArgumentException;

	default CollectionElement<E> create() {
		return create(null);
	}

	CollectionElement<E> create(Consumer<? super E> preAddAction);
}
