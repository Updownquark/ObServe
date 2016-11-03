package org.observe.assoc.impl;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.ObservableValue;
import org.observe.assoc.ObservableMultiMap;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.impl.ObservableArrayList;
import org.observe.collect.impl.ObservableHashSet;
import org.observe.collect.impl.ObservableTreeSet;
import org.observe.util.DefaultTransactable;
import org.qommons.Transactable;
import org.qommons.Transaction;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A default implementation of {@link ObservableMultiMap}
 *
 * @param <K> The type of keys used in this map
 * @param <V> The type of values stored in this map
 */
public class ObservableMultiMapImpl<K, V> implements ObservableMultiMap<K, V> {
	private static class DefaultMultiMapEntry<K, V> extends ObservableArrayList<V> implements ObservableMultiEntry<K, V> {
		private final K theKey;

		DefaultMultiMapEntry(K key, TypeToken<K> keyType, TypeToken<V> valueType, ReentrantReadWriteLock lock,
			ObservableValue<CollectionSession> session, Transactable controller) {
			super(valueType, lock, session, controller);
			theKey = key;
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theKey);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof MultiEntry && Objects.equals(((MultiEntry<?, ?>) o).getKey(), theKey);
		}
	}

	private final TypeToken<K> theKeyType;
	private final TypeToken<V> theValueType;

	private DefaultTransactable theSessionController;
	private final ReentrantReadWriteLock theLock;

	private final ObservableSet<? extends ObservableMultiEntry<K, V>> theEntries;
	private final MultiEntryCreator<K, V> theEntryCreator;

	/**
	 * @param keyType The type of key used by this map
	 * @param valueType The type of value stored in this map
	 */
	public ObservableMultiMapImpl(TypeToken<K> keyType, TypeToken<V> valueType) {
		this(keyType, valueType, ObservableHashSet.creator());
	}

	/**
	 * @param keyType The type of key used by this map
	 * @param valueType The type of value stored in this map
	 * @param entrySet Creates the set to store this collection's entries
	 */
	public <E extends ObservableMultiEntry<K, V>> ObservableMultiMapImpl(TypeToken<K> keyType, TypeToken<V> valueType,
		CollectionCreator<E, ? extends ObservableSet<E>> entrySet) {
		this(keyType, valueType,
			new TypeToken<E>() {}.where(new TypeParameter<K>() {}, keyType).where(new TypeParameter<V>() {}, valueType), entrySet,
			(key, keyTyp, valueTyp, lock, session, controller) -> new DefaultMultiMapEntry<>(key, keyType, valueType, lock, session,
				controller));
	}

	/**
	 * @param keyType The type of key used by this map
	 * @param valueType The type of value stored in this map
	 * @param entryType The type of entry that the entry creator will create
	 * @param entrySet Creates the set to store this map's entries
	 * @param entryCreator Creates individual map entries for this map
	 */
	public <E extends ObservableMultiEntry<K, V>> ObservableMultiMapImpl(TypeToken<K> keyType, TypeToken<V> valueType,
		TypeToken<E> entryType, CollectionCreator<E, ? extends ObservableSet<E>> entrySet,
			MultiEntryCreator<K, V> entryCreator) {
		theKeyType = keyType.wrap();
		theValueType = valueType.wrap();
		theLock=new ReentrantReadWriteLock();
		theSessionController = new DefaultTransactable(theLock);

		theEntries = entrySet.create(entryType, theLock, theSessionController.getSession(), theSessionController);
		theEntryCreator = entryCreator;
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
	public boolean isSafe() {
		return true;
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
	public ObservableSet<? extends ObservableMultiEntry<K, V>> entrySet() {
		return theEntries;
	}

	/**
	 * @param key The key to create the entry for
	 * @return The new entry for the key
	 */
	protected ObservableMultiEntry<K, V> createEntry(K key) {
		return theEntryCreator.create(key, theKeyType, theValueType, theLock, theSessionController.getSession(), theSessionController);
	}

	@Override
	public boolean add(K key, V value) {
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			ObservableMultiEntry<K, V> keyedEntry = null;
			for(ObservableMultiEntry<K, V> entry : theEntries)
				if(Objects.equals(entry.getKey(), key)) {
					keyedEntry = entry;
					break;
				}
			if(keyedEntry == null) {
				keyedEntry = createEntry(key);
				((ObservableSet<ObservableMultiEntry<K, V>>) (ObservableSet<?>) theEntries).add(keyedEntry);
			}
			return keyedEntry.add(value);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean addAll(K key, Collection<? extends V> values) {
		try (Transaction trans = lock(true, null)) {
			ObservableMultiEntry<K, V> keyedEntry = null;
			for(ObservableMultiEntry<K, V> entry : theEntries)
				if(Objects.equals(entry.getKey(), key)) {
					keyedEntry = entry;
					break;
				}
			if(keyedEntry == null) {
				keyedEntry = createEntry(key);
				((ObservableSet<ObservableMultiEntry<K, V>>) (ObservableSet<?>) theEntries).add(keyedEntry);
			}
			return keyedEntry.addAll(values);
		}
	}

	@Override
	public boolean remove(K key, Object value) {
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			Iterator<? extends ObservableMultiEntry<K, V>> entryIter = theEntries.iterator();
			while(entryIter.hasNext()) {
				ObservableMultiEntry<K, V> entry = entryIter.next();
				if(Objects.equals(entry.getKey(), key)) {
					int size = entry.size();
					if (size == 1 && Objects.equals(entry.iterator().next(), value)) {
						entryIter.remove();
						return true;
					} else if (size == 1)
						return false; // Values not equal
					else
						return entry.remove(value);
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
				ObservableMultiEntry<K, V> entry = entryIter.next();
				if(Objects.equals(entry.getKey(), key)) {
					boolean entryEmpty = entry.isEmpty();
					entryIter.remove();
					return !entryEmpty;
				}
			}
			return false;
		}
	}

	/**
	 * @param valueCompare The comparator to sort the values by
	 * @return An entry creator that sorts its values (extends {@link ObservableTreeSet})
	 */
	public static <K, V> MultiEntryCreator<K, V> sortedEntryCreator(Comparator<? super V> valueCompare) {
		return (key, keyType, valueType, lock, session, controller) -> new SortedMultiEntry<>(key, valueType, lock, session, controller,
			valueCompare);
	}

	/**
	 * A sorted multi-entry implementation
	 *
	 * @param <K> The key-type for the entry
	 * @param <V> The value-type for the entry
	 */
	public static class SortedMultiEntry<K, V> extends ObservableTreeSet<V> implements ObservableMultiEntry<K, V> {
		private final K theKey;

		/**
		 * @param key The key for the entry
		 * @param type The type of values in the entry
		 * @param lock The lock for this collection to use
		 * @param session The session for this collection to use (see {@link #getSession()})
		 * @param sessionController The controller for the session. May be null, in which case the transactional methods in this collection
		 *        will not actually create transactions.
		 * @param compare The comparator to sort this set's elements. Use {@link Comparable}::{@link Comparable#compareTo(Object) compareTo}
		 *        for natural ordering.
		 */
		public SortedMultiEntry(K key, TypeToken<V> type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session,
			Transactable sessionController, Comparator<? super V> compare) {
			super(type, lock, session, sessionController, compare);
			theKey = key;
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theKey);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof MultiEntry && Objects.equals(theKey, ((MultiEntry<?, ?>) obj).getKey());
		}
	}
}
