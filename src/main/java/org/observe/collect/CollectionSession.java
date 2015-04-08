package org.observe.collect;

/**
 * Available from {@link ObservableCollection#getSession()} for listeners to store state information, allowing them to take advantage of
 * batched operations during a transaction.
 */
public interface CollectionSession {
	/** @return The cause of the set of modifications. May be null. */
	Object getCause();

	/**
	 * @param listener The listener requesting the value
	 * @param key The listener-specific key of the value
	 * @return The value previously stored via {@link #put(Object, String, Object)} for the same listener and value
	 */
	Object get(Object listener, String key);

	/**
	 * @param listener The listener requesting to store a value
	 * @param key The listener-specific key to store the value under
	 * @param value The value to store for the given listener and key
	 * @return The value that was previously stored in this session for the same listener-key pair
	 */
	Object put(Object listener, String key, Object value);

	/**
	 * Stores a value in this session if no value is currently stored for the given listener and key
	 * 
	 * @param listener The listener requesting to store a value
	 * @param key The listener-specific key to store the value under
	 * @param value The value to store for the given listener and key
	 * @return The value that was previously stored in this session for the same listener-key pair
	 */
	Object putIfAbsent(Object listener, String key, Object value);
}
