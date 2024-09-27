package org.observe;

import org.qommons.collect.CollectionElement;

/**
 * An event that is fired on some single-value holder such as an {@link ObservableValue} or a {@link CollectionElement}
 *
 * @param <T> The type of the value
 */
public interface ValueChangeEvent<T> {
	/** @return Whether this represents the population of the initial value in response to subscription */
	boolean isInitial();

	/** @return The old value */
	T getOldValue();

	/** @return The new value */
	T getNewValue();

	/** @return Whether this event represents an update, i.e. an event that's fired even though the value reference hasn't changed */
	default boolean isUpdate() {
		if (isInitial())
			return false;
		else
			return getOldValue() == getNewValue();
	}
}
