package org.observe.assoc.impl;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.ObservableValue;
import org.observe.assoc.ObservableMultiMap.ObservableMultiEntry;
import org.observe.collect.CollectionSession;
import org.qommons.Transactable;

import com.google.common.reflect.TypeToken;

public interface MultiEntryCreator<K, V> {
	ObservableMultiEntry<K, V> create(K key, TypeToken<K> keyType, TypeToken<V> valueType, ReentrantReadWriteLock lock,
		ObservableValue<CollectionSession> session, Transactable sessionController);
}
