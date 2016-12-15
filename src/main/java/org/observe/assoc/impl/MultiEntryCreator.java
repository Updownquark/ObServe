package org.observe.assoc.impl;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.ObservableValue;
import org.observe.assoc.ObservableMultiMap.ObservableMultiEntry;
import org.observe.collect.CollectionSession;
import org.qommons.Transactable;

import com.google.common.reflect.TypeToken;

/**
 * Allows the user to create custom multi-entry implementations for {@link ObservableMultiMapImpl}
 * 
 * @param <K> The key-type
 * @param <V> The value-type
 */
public interface MultiEntryCreator<K, V> {
	/**
	 * @param key The key to create the entry for
	 * @param keyType The key type for the entry
	 * @param valueType The value type for the entry
	 * @param lock The lock to back the entry's transactionality
	 * @param session The session to back the entry's transactionality
	 * @param sessionController The controller for the entry's transactionality
	 * @return The new entry to use in the multi-map
	 */
	ObservableMultiEntry<K, V> create(K key, TypeToken<K> keyType, TypeToken<V> valueType, ReentrantReadWriteLock lock,
		ObservableValue<CollectionSession> session, Transactable sessionController);
}
