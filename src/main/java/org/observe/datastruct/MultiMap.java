package org.observe.datastruct;

import java.util.Collection;
import java.util.Set;

/**
 * A map that can store more than one value per key. The behavior of the collections associated with the keys are implementation-specific.
 * For example, some implementations may accept duplicate key-value pairs, while others may enforce uniqueness.
 * 
 * @param <K> The key type for the map
 * @param <V> The value type for the map
 */
public interface MultiMap<K, V> {
	/** @return The keys that have least one value in this map */
	Set<K> keySet();

	/**
	 * @param key The key to get values for
	 * @return The collection of values stored for the given key in this map. Never null.
	 */
	Collection<V> get(Object key);

	/**
	 * Adds a value for the given key
	 * 
	 * @param key The key to add the value for
	 * @param value The value to add for the key
	 * @return Whether this map was changed as a result of the call
	 */
	boolean add(K key, V value);

	/**
	 * Adds a number of values for a key
	 * 
	 * @param key The key to add the values for
	 * @param values The values to add for the key
	 * @return Whether this map was changed as a result of the call
	 */
	boolean addAll(K key, Collection<? extends V> values);

	/**
	 * Removes a value associated with a key
	 * 
	 * @param key The key to remove the value for
	 * @param value The value to remove
	 * @return Whether this map was changed as a result of the call
	 */
	boolean remove(K key, Object value);

	/**
	 * Removes all values stored by a given key
	 * 
	 * @param key The key to remove all the values of
	 * @return Whether this map was changed as a result of the call
	 */
	boolean removeAll(K key);
}
