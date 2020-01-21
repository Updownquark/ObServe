package org.observe.entity;

import java.util.function.Function;

import com.google.common.reflect.TypeToken;

/**
 * Obtains some information about an entity
 *
 * @param <E> The type of the entity
 * @param <F> The type of the information to access
 */
public interface EntityValueAccess<E, F> {
	TypeToken<F> getValueType();

	ObservableEntityType<F> getTargetEntity();

	String canAccept(F value);

	<T> EntityValueAccess<E, T> dot(Function<? super F, T> attr);

	F getValue(E entity);
}
