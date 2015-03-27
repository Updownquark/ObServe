package org.observe;

/**
 * A simple action to perform on a value
 * 
 * @param <T> The type of value to act on
 */
public interface Action<T> {
	/** @param value The value to act on */
	void act(T value);
}
