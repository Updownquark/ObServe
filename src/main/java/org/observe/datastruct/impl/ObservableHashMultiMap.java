package org.observe.datastruct.impl;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.ObservableValue;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.impl.ObservableArrayList;
import org.observe.collect.impl.ObservableHashSet;
import org.observe.datastruct.ObservableMultiMap;
import org.observe.util.DefaultTransactable;
import org.observe.util.Transaction;

import prisms.lang.Type;

/**
 * A default implementation of {@link ObservableMultiMap}
 *
 * @param <K> The type of keys used in this map
 * @param <V> The type of values stored in this map
 */
public class ObservableHashMultiMap<K, V> implements ObservableMultiMap<K, V> {
	private class DefaultMultiMapEntry extends ObservableArrayList<V> implements ObservableMultiEntry<K, V> {

		private final K theKey;

		DefaultMultiMapEntry(K key) {
			super(theValueType, theLock, theSessionController.getSession(), theSessionController);
			theKey = key;
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public Type getType() {
			return theValueType;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Map.Entry && Objects.equals(((Map.Entry<?, ?>) o).getKey(), theKey);
		}

		private boolean addValue(V value) {
			return add(value);
		}

		private boolean addAllValues(Collection<? extends V> values) {
			return addAll(values);
		}

		private boolean removeValue(Object value) {
			return remove(value);
		}
	}

	private final Type theKeyType;
	private final Type theValueType;

	private DefaultTransactable theSessionController;
	private final ReentrantReadWriteLock theLock;

	private final ObservableSet<DefaultMultiMapEntry> theEntries;

	/**
	 * @param keyType The type of key used by this map
	 * @param valueType The type of value stored in this map
	 */
	public ObservableHashMultiMap(Type keyType, Type valueType) {
		theKeyType = keyType;
		theValueType = valueType;
		theLock=new ReentrantReadWriteLock();
		theSessionController = new DefaultTransactable(theLock);

		theEntries = new ObservableHashSet<>(new Type(DefaultMultiMapEntry.class), theLock, theSessionController.getSession(),
			theSessionController);
	}

	@Override
	public Type getKeyType() {
		return theKeyType;
	}

	@Override
	public Type getValueType() {
		return theValueType;
	}

	@Override
	public ObservableValue<CollectionSession> getSession() {
		return theSessionController.getSession();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theSessionController.lock(write, cause);
	}

	@Override
	public ObservableSet<K> keySet() {
		return ObservableMultiMap.defaultKeySet(this);
	}

	@Override
	public ObservableCollection<V> get(Object key) {
		return ObservableMultiMap.defaultGet(this, key);
	}

	@Override
	public ObservableSet<? extends ObservableMultiEntry<K, V>> observeEntries() {
		return theEntries;
	}

	@Override
	public boolean add(K key, V value) {
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			DefaultMultiMapEntry keyedEntry = null;
			for(ObservableMultiEntry<K, V> entry : theEntries)
				if(Objects.equals(entry.getKey(), key)) {
					keyedEntry = (DefaultMultiMapEntry) entry;
					break;
				}
			if(keyedEntry == null) {
				keyedEntry = new DefaultMultiMapEntry(key);
				theEntries.add(keyedEntry);
			}
			return keyedEntry.addValue(value);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean addAll(K key, Collection<? extends V> values) {
		try (Transaction trans = lock(true, null)) {
			DefaultMultiMapEntry keyedEntry = null;
			for(ObservableMultiEntry<K, V> entry : theEntries)
				if(Objects.equals(entry.getKey(), key)) {
					keyedEntry = (DefaultMultiMapEntry) entry;
					break;
				}
			if(keyedEntry == null) {
				keyedEntry = new DefaultMultiMapEntry(key);
				theEntries.add(keyedEntry);
			}
			return keyedEntry.addAllValues(values);
		}
	}

	@Override
	public boolean remove(K key, Object value) {
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			Iterator<? extends ObservableMultiEntry<K, V>> entryIter = theEntries.iterator();
			while(entryIter.hasNext()) {
				DefaultMultiMapEntry entry = (DefaultMultiMapEntry) entryIter.next();
				if(Objects.equals(entry.getKey(), key)) {
					int size = entry.size();
					if(size == 1 && Objects.equals(entry.get(0), value)) {
						entryIter.remove();
						return true;
					} else if(size != 1)
						return false; // Values not equal
					else
						return entry.removeValue(value);
				}
			}
			return false;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean removeAll(K key) {
		try (Transaction trans = lock(true, null)) {
			Iterator<? extends ObservableMultiEntry<K, V>> entryIter = theEntries.iterator();
			while(entryIter.hasNext()) {
				DefaultMultiMapEntry entry = (DefaultMultiMapEntry) entryIter.next();
				if(Objects.equals(entry.getKey(), key)) {
					boolean entryEmpty = entry.isEmpty();
					entryIter.remove();
					return !entryEmpty;
				}
			}
			return false;
		}
	}
}