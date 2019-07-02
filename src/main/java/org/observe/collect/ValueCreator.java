package org.observe.collect;

import java.util.function.Function;

import org.qommons.collect.CollectionElement;

public interface ValueCreator<E> {
	ValueCreator<E> with(String fieldName, Object value) throws IllegalArgumentException;

	<F> ValueCreator<E> with(Function<? super E, F> field, F value) throws IllegalArgumentException, UnsupportedOperationException;

	CollectionElement<E> create();
}
