package org.observe.collect;

import java.util.function.Function;

import org.qommons.collect.CollectionElement;

public interface ValueCreator<E> {
	<F> ValueCreator<E> with(Function<? super E, F> field, F value);

	CollectionElement<E> create();
}
