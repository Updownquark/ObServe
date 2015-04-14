package org.observe.datastruct;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.DefaultObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.collect.*;
import org.observe.util.Transaction;

import prisms.lang.Type;

/**
 * A default implementation of {@link ObservableMap}
 *
 * @param <K> The type of keys used in this map
 * @param <V> The type of values stored in this map
 */
public class DefaultObservableMap<K, V> extends java.util.AbstractMap<K, V> implements ObservableMap<K, V>, org.observe.util.Transactable {
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
			theController.onNext(new ObservableValueEvent<>(this, oldValue, theValue, null));
			return oldValue;
		}

		private V remove() {
			V oldValue = theValue;
			theValue = null;
			theController.onCompleted(new ObservableValueEvent<>(this, oldValue, oldValue, null));
			return oldValue;
		}

		@Override
		public Type getType() {
			return theValueType;
		}

		@Override
		public V get() {
			return getValue();
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Map.Entry && Objects.equals(((Map.Entry<?, ?>) o).getKey(), theKey);
		}
	}

	private final Type theKeyType;
	private final Type theValueType;

	private CollectionSession theSession;
	private DefaultObservableValue<CollectionSession> theSessionObservable;
	private org.observe.Observer<ObservableValueEvent<CollectionSession>> theSessionController;

	private final ReentrantReadWriteLock theLock;
	private final ObservableSet<ObservableEntry<K, V>> theEntries;
	private final Set<ObservableEntry<K, V>> theEntryController;

	/**
	 * @param keyType The type of key used by this map
	 * @param valueType The type of value stored in this map
	 */
	public DefaultObservableMap(Type keyType, Type valueType) {
		theKeyType = keyType;
		theValueType = valueType;
		theLock=new ReentrantReadWriteLock();
		theSessionObservable = new DefaultObservableValue<CollectionSession>() {
			private final Type theSessionType = new Type(CollectionSession.class);

			@Override
			public Type getType() {
				return theSessionType;
			}

			@Override
			public CollectionSession get() {
				return theSession;
			}
		};
		theSessionController = theSessionObservable.control(null);

		theEntries = new DefaultObservableSet<ObservableEntry<K, V>>(new Type(ObservableEntry.class, theKeyType, theKeyType), theLock,
			theSessionObservable) {
		};
		theEntryController = ((DefaultObservableSet<ObservableEntry<K, V>>) theEntries).control(null);
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
	public ObservableCollection<ObservableEntry<K, V>> observeEntries() {
		return theEntries;
	}

	@Override
	public Transaction startTransaction(Object cause) {
		Lock lock = theLock.writeLock();
		lock.lock();
		theSession = new DefaultCollectionSession(cause);
		theSessionController.onNext(new ObservableValueEvent<>(theSessionObservable, null, theSession, cause));
		return new org.observe.util.Transaction() {
			@Override
			public void close() {
				if(theLock.getWriteHoldCount() != 1) {
					lock.unlock();
					return;
				}
				CollectionSession session = theSession;
				theSession = null;
				theSessionController.onNext(new ObservableValueEvent<>(theSessionObservable, session, null, cause));
				lock.unlock();
			}
		};
	}

	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		return (Set<Map.Entry<K, V>>) (Set<? extends Map.Entry<K, V>>) theEntries;
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
				theEntryController.add(newEntry);
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
			Iterator<ObservableEntry<K, V>> entryIter = theEntryController.iterator();
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
		try (Transaction trans = startTransaction(null);) {
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
					theEntryController.add(newEntry);
				}
			}
		}
	}
}
