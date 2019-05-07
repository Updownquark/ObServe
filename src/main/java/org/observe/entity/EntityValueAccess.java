package org.observe.entity;

import java.util.function.Function;

/**
 * Obtains some information about an entity
 * 
 * @param <E> The type of the entity
 * @param <F> The type of the information to access
 */
public interface EntityValueAccess<E, F> {
	<T> EntityValueAccess<E, T> dot(Function<? super E, T> attr);
}
