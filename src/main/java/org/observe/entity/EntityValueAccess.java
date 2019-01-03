package org.observe.entity;

import java.util.function.Function;

public interface EntityValueAccess<E, F> {
	<T> EntityValueAccess<E, T> dot(Function<? super E, T> attr);
}
