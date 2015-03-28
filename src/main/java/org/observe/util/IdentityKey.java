package org.observe.util;

/**
 * A wrapper that allows values to be added to sets or maps by identity instead of by value
 * 
 * @param <T> The type of the value to wrap
 */
public class IdentityKey<T> {
	/** The wrapped value */
	public final T value;

	/** @param val The value to wrap */
	public IdentityKey(T val) {
		value = val;
	}

	@Override
	public int hashCode() {
		return System.identityHashCode(value);
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof IdentityKey && ((IdentityKey<?>) obj).value == value;
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
