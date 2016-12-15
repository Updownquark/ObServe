package org.observe.assoc.impl;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.ObservableValue;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;
import org.observe.collect.impl.ObservableTreeSet;
import org.qommons.Transactable;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * Default sorted multi-map implementation
 *
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public class ObservableSortedMultiMapImpl<K, V> extends ObservableMultiMapImpl<K, V> implements ObservableSortedMultiMap<K, V> {
	/**
	 * @param keyType The key type of the map
	 * @param valueType The value type of the map
	 * @param compare The comparator to sort the keys
	 */
	public ObservableSortedMultiMapImpl(TypeToken<K> keyType, TypeToken<V> valueType, Comparator<? super K> compare) {
		this(keyType, valueType, defaultCreator(keyType, valueType, compare));
	}

	/**
	 * @param keyType The key type of the set
	 * @param valueType The value type of the set
	 * @param compare The comparator to sort the keys
	 * @return The default collection creator for entry sets in this sorted multi map class
	 */
	public static <K, V> CollectionCreator<ObservableSortedMultiEntry<K, V>, ObservableSortedSet<ObservableSortedMultiEntry<K, V>>> defaultCreator(
		TypeToken<K> keyType, TypeToken<V> valueType, Comparator<? super K> compare) {
		return new CollectionCreator<ObservableSortedMultiEntry<K, V>, ObservableSortedSet<ObservableSortedMultiEntry<K, V>>>() {
			@Override
			public ObservableSortedSet<ObservableSortedMultiEntry<K, V>> create(TypeToken<ObservableSortedMultiEntry<K, V>> type,
				ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session, Transactable sessionController) {
				TypeToken<ObservableSortedMultiEntry<K, V>> setType = new TypeToken<ObservableSortedMultiEntry<K, V>>() {}
				.where(new TypeParameter<K>() {}, keyType).where(new TypeParameter<V>() {}, valueType);
				return new ObservableTreeSet<>(setType, lock, session, sessionController,
					(entry1, entry2) -> compare.compare(entry1.getKey(), entry2.getKey()));
			}
		};
	}

	/**
	 * @param keyType The key type for the map
	 * @param valueType The value type for the map
	 * @param entrySet The collection creator for the entry set
	 */
	public <E extends ObservableSortedMultiEntry<K, V>> ObservableSortedMultiMapImpl(TypeToken<K> keyType, TypeToken<V> valueType,
		CollectionCreator<E, ? extends ObservableSortedSet<ObservableSortedMultiEntry<K, V>>> entrySet) {
		super(keyType, valueType,
			(CollectionCreator<ObservableMultiEntry<K, V>, ObservableSet<ObservableMultiEntry<K, V>>>) (CollectionCreator<?, ?>) entrySet);
	}

	/**
	 * @param keyType The key type for the map
	 * @param valueType The value type for the map
	 * @param entryType The type of entry that the entry creator will create
	 * @param entrySet The collection creator for the entry set
	 * @param entryCreator Creates individual map entries for this map
	 */
	public <E extends ObservableSortedMultiEntry<K, V>> ObservableSortedMultiMapImpl(TypeToken<K> keyType, TypeToken<V> valueType,
		TypeToken<E> entryType, CollectionCreator<E, ? extends ObservableSortedSet<E>> entrySet, MultiEntryCreator<K, V> entryCreator) {
		super(keyType, valueType, entryType, entrySet, entryCreator);
	}

	@Override
	public ObservableSortedSet<K> keySet() {
		return (ObservableSortedSet<K>) super.keySet();
	}

	@Override
	public ObservableSortedSet<ObservableSortedMultiEntry<K, V>> entrySet() {
		return (ObservableSortedSet<ObservableSortedMultiEntry<K, V>>) super.entrySet();
	}

	/**
	 * @param keyCompare The comparator to sort the keys b
	 * @param valueCompare The comparator to sort the values by
	 * @return An entry creator that sorts its values (extends {@link ObservableTreeSet})
	 */
	public static <K, V> MultiEntryCreator<K, V> sortedEntryCreator(Comparator<? super K> keyCompare, Comparator<? super V> valueCompare) {
		return (key, keyType, valueType, lock, session, controller) -> new DoubleSortedMultiEntry<>(key, valueType, lock, session,
			controller, keyCompare, valueCompare);
	}

	/**
	 * A sorted multi-entry implementation
	 *
	 * @param <K> The key-type for the entry
	 * @param <V> The value-type for the entry
	 */
	public static class DoubleSortedMultiEntry<K, V> extends ObservableTreeSet<V> implements ObservableSortedMultiEntry<K, V> {
		private final K theKey;
		private final Comparator<? super K> theKeyCompare;

		/**
		 * @param key The key for the entry
		 * @param type The type of values in the entry
		 * @param lock The lock for this collection to use
		 * @param session The session for this collection to use (see {@link #getSession()})
		 * @param sessionController The controller for the session. May be null, in which case the transactional methods in this collection
		 *        will not actually create transactions.
		 * @param keyCompare The comparator to sort the keys by in the map
		 * @param valueCompare The comparator to sort this set's elements. Use {@link Comparable}::{@link Comparable#compareTo(Object)
		 *        compareTo} for natural ordering.
		 */
		public DoubleSortedMultiEntry(K key, TypeToken<V> type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session,
			Transactable sessionController, Comparator<? super K> keyCompare, Comparator<? super V> valueCompare) {
			super(type, lock, session, sessionController, valueCompare);
			theKey = key;
			theKeyCompare = keyCompare;
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public int compareTo(ObservableSortedMultiEntry<K, V> o) {
			return theKeyCompare.compare(theKey, o.getKey());
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theKey);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof MultiEntry && Objects.equals(((MultiEntry<?, ?>) obj).getKey(), theKey);
		}
	}
}
