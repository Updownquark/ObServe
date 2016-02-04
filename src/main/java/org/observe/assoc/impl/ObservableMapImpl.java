package org.observe.assoc.impl;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.DefaultObservableValue;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.assoc.ObservableMap;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableSet;
import org.observe.collect.impl.ObservableHashSet;
import org.observe.util.DefaultTransactable;
import org.qommons.Transaction;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A default implementation of {@link ObservableMap}
 *
 * @param <K> The type of keys used in this map
 * @param <V> The type of values stored in this map
 */
public class ObservableMapImpl<K, V> implements ObservableMap<K, V> {
	private class DefaultMapEntry extends DefaultObservableValue<V> implements ObservableEntry<K, V> {
		private final Observer<ObservableValueEvent<V>> theController = control(null);

		private final K theKey;
		private V theValue;

		DefaultMapEntry(K key) {
			theKey = key;
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public V getValue() {
			return theValue;
		}

		@Override
		public V setValue(V value) {
			V oldValue = theValue;
			theValue = value;
			theController.onNext(createChangeEvent(oldValue, theValue, null));
			return oldValue;
		}

		private V remove() {
			V oldValue = theValue;
			theValue = null;
			theController.onCompleted(createChangeEvent(oldValue, oldValue, null));
			return oldValue;
		}

		@Override
		public TypeToken<V> getType() {
			return theValueType;
		}

		@Override
		public V get() {
			return getValue();
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theKey);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Map.Entry && Objects.equals(((Map.Entry<?, ?>) o).getKey(), theKey);
		}
	}

	private final TypeToken<K> theKeyType;

	private final TypeToken<V> theValueType;

	private DefaultTransactable theSessionController;
	private final ReentrantReadWriteLock theLock;

	private final ObservableSet<ObservableEntry<K, V>> theEntries;

	/**
	 * @param keyType The type of key used by this map
	 * @param valueType The type of value stored in this map
	 */
	public ObservableMapImpl(TypeToken<K> keyType, TypeToken<V> valueType) {
		this(keyType, valueType, ObservableHashSet::new);
	}

	/**
	 * @param keyType The type of key used by this map
	 * @param valueType The type of value stored in this map
	 * @param entrySet Creates the set to hold this map's entries
	 */
	public ObservableMapImpl(TypeToken<K> keyType, TypeToken<V> valueType,
		CollectionCreator<ObservableEntry<K, V>, ObservableSet<ObservableEntry<K, V>>> entrySet) {
		theKeyType = keyType.wrap();
		theValueType = valueType.wrap();
		theLock=new ReentrantReadWriteLock();
		theSessionController = new DefaultTransactable(theLock);

		theEntries = entrySet.create(
			new TypeToken<ObservableEntry<K, V>>() {}.where(new TypeParameter<K>() {}, theKeyType).where(new TypeParameter<V>() {},
				theValueType), theLock, theSessionController.getSession(), theSessionController);
	}

	@Override
	public TypeToken<K> getKeyType() {
		return theKeyType;
	}

	@Override
	public TypeToken<V> getValueType() {
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
		return ObservableMap.defaultKeySet(this);
	}

	@Override
	public ObservableValue<V> observe(Object key) {
		return ObservableMap.defaultObserve(this, key);
	}

	@Override
	public ObservableSet<ObservableEntry<K, V>> observeEntries() {
		return theEntries;
	}

	@Override
	public V put(K key, V value) {
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			boolean found = false;
			V oldValue = null;
			for(ObservableEntry<K, V> entry : theEntries)
				if(Objects.equals(entry.getKey(), key)) {
					found = true;
					oldValue = entry.setValue(value);
					break;
				}
			if(!found) {
				DefaultMapEntry newEntry = new DefaultMapEntry(key);
				newEntry.setValue(value);
				theEntries.add(newEntry);
			}
			return found ? oldValue : null;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public V remove(Object key) {
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			boolean found = false;
			V oldValue = null;
			Iterator<ObservableEntry<K, V>> entryIter = theEntries.iterator();
			while(entryIter.hasNext()) {
				DefaultMapEntry entry = (DefaultMapEntry) entryIter.next();
				if(Objects.equals(entry.getKey(), key)) {
					found = true;
					entryIter.remove();
					oldValue = entry.remove();
					break;
				}
			}
			return found ? oldValue : null;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		try (Transaction trans = lock(true, null)) {
			for(Map.Entry<? extends K, ? extends V> mEntry : m.entrySet()) {
				boolean found = false;
				for(ObservableEntry<K, V> entry : theEntries)
					if(Objects.equals(entry.getKey(), mEntry.getKey())) {
						found = true;
						entry.setValue(mEntry.getValue());
						break;
					}
				if(!found) {
					DefaultMapEntry newEntry = new DefaultMapEntry(mEntry.getKey());
					newEntry.setValue(mEntry.getValue());
					theEntries.add(newEntry);
				}
			}
		}
	}
}
