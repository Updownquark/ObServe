package org.observe;

import com.google.common.reflect.TypeToken;

/**
 * An object that either contains or produces values with a run-time-specified type
 * 
 * @param <T> The type of the value that this object contains or produces
 */
public interface TypedValueContainer<T> {
	/** @return The type of value that this object contains or produces */
	TypeToken<T> getType();
}
