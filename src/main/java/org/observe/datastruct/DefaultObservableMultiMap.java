package org.observe.datastruct;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.DefaultObservableValue;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.collect.*;
import org.observe.util.Transaction;

import prisms.lang.Type;

/**
 * A default implementation of {@link ObservableMultiMap}
 *
 * @param <K> The type of keys used in this map
 * @param <V> The type of values stored in this map
 */
public class DefaultObservableMultiMap<K, V> implements ObservableMultiMap<K, V>, org.observe.util.Transactable {
	private class DefaultMultiMapEntry extends DefaultObservableList<V> implements ObservableMultiEntry<K, V> {
		private final TransactableList<V> theController = control(null);

		private final K theKey;

		DefaultMultiMapEntry(K key) {
			super(theValueType, theLock, theSessionObservable);
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
			return theController.add(value);
		}

		private boolean addAllValues(Collection<? extends V> values) {
			return theController.addAll(values);
		}

		private boolean removeValue(Object value) {
			return theController.remove(value);
		}
	}

	private final Type theKeyType;
	private final Type theValueType;

	private CollectionSession theSession;
	private DefaultObservableValue<CollectionSession> theSessionObservable;
	private org.observe.Observer<ObservableValueEvent<CollectionSession>> theSessionController;

	private final ReentrantReadWriteLock theLock;

	private final ObservableSet<ObservableMultiEntry<K, V>> theEntries;

	private final Set<ObservableMultiEntry<K, V>> theEntryController;

	/**
	 * @param keyType The type of key used by this map
	 * @param valueType The type of value stored in this map
	 */
	public DefaultObservableMultiMap(Type keyType, Type valueType) {
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

		theEntries = new DefaultObservableSet<ObservableMultiEntry<K, V>>(new Type(ObservableMultiEntry.class, theKeyType, theKeyType),
			theLock,
			theSessionObservable) {
		};
		theEntryController = ((DefaultObservableSet<ObservableMultiEntry<K, V>>) theEntries).control(null);
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
		return theSessionObservable;
	}

	@Override
	public ObservableCollection<ObservableMultiEntry<K, V>> observeEntries() {
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
				theEntryController.add(keyedEntry);
			}
			return keyedEntry.addValue(value);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean addAll(K key, Collection<? extends V> values) {
		try (Transaction trans = startTransaction(null)) {
			DefaultMultiMapEntry keyedEntry = null;
			for(ObservableMultiEntry<K, V> entry : theEntries)
				if(Objects.equals(entry.getKey(), key)) {
					keyedEntry = (DefaultMultiMapEntry) entry;
					break;
				}
			if(keyedEntry == null) {
				keyedEntry = new DefaultMultiMapEntry(key);
				theEntryController.add(keyedEntry);
			}
			return keyedEntry.addAllValues(values);
		}
	}

	@Override
	public boolean remove(K key, Object value) {
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			Iterator<ObservableMultiEntry<K, V>> entryIter = theEntryController.iterator();
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
		try (Transaction trans = startTransaction(null)) {
			Iterator<ObservableMultiEntry<K, V>> entryIter = theEntryController.iterator();
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
