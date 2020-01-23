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
	/** @return The type of information */
	TypeToken<F> getValueType();

	/** @return The type of entity that this object can access information for */
	ObservableEntityType<F> getTargetEntity();

	/**
	 * @param value The value to check
	 * @return A message detailing why the given value cannot be a value retrieved by this access, or null if the value is valid for this
	 *         access
	 */
	String canAccept(F value);

	/**
	 * @param attr A recognized attribute of this object's type to get
	 * @return A new value access object to get the given information from this object's access on an entity
	 */
	<T> EntityValueAccess<E, T> dot(Function<? super F, T> attr);

	/**
	 * @param entity The entity to get the information from
	 * @return The information specified by this access
	 */
	F getValue(E entity);
}
