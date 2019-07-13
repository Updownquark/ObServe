package org.observe.config;

import java.util.function.Function;

import org.qommons.collect.CollectionElement;

public interface ValueCreator<E> {
	ConfiguredValueType<E> getType();

	ValueCreator<E> with(String fieldName, Object value) throws IllegalArgumentException;

	<F> ValueCreator<E> with(ConfiguredValueField<? super E, F> field, F value) throws IllegalArgumentException;

	<F> ValueCreator<E> with(Function<? super E, F> field, F value) throws IllegalArgumentException;

	CollectionElement<E> create();
}
