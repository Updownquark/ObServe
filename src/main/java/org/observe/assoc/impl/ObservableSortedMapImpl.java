package org.observe.assoc.impl;

import java.util.Comparator;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.ObservableValue;
import org.observe.assoc.ObservableSortedMap;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;
import org.observe.collect.impl.ObservableTreeSet;
import org.qommons.Transactable;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * Default sorted map implementation
 * 
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public class ObservableSortedMapImpl<K, V> extends ObservableMapImpl<K, V> implements ObservableSortedMap<K, V> {
	/**
	 * @param keyType The key type of the map
	 * @param valueType The value type of the map
	 * @param compare The comparator to sort the keys
	 */
	public ObservableSortedMapImpl(TypeToken<K> keyType, TypeToken<V> valueType, Comparator<? super K> compare) {
		super(keyType, valueType, defaultCreator(keyType, valueType, compare));
	}

	/**
	 * @param keyType The key type of the set
	 * @param valueType The value type of the set
	 * @param compare The comparator to sort the keys
	 * @return The default collection creator for entry sets in this sorted map class
	 */
	public static <K, V> CollectionCreator<ObservableEntry<K, V>, ObservableSet<ObservableEntry<K, V>>> defaultCreator(TypeToken<K> keyType,
			TypeToken<V> valueType, Comparator<? super K> compare) {
		return new CollectionCreator<ObservableEntry<K, V>, ObservableSet<ObservableEntry<K, V>>>() {
			@Override
			public ObservableSet<ObservableEntry<K, V>> create(TypeToken<ObservableEntry<K, V>> type, ReentrantReadWriteLock lock,
					ObservableValue<CollectionSession> session, Transactable sessionController) {
				TypeToken<ObservableEntry<K, V>> setType = new TypeToken<ObservableEntry<K, V>>() {}
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
	public ObservableSortedMapImpl(TypeToken<K> keyType, TypeToken<V> valueType,
			CollectionCreator<ObservableEntry<K, V>, ObservableSortedSet<ObservableEntry<K, V>>> entrySet) {
		super(keyType, valueType,
				(CollectionCreator<ObservableEntry<K, V>, ObservableSet<ObservableEntry<K, V>>>) (CollectionCreator<?, ?>) entrySet);
	}

	@Override
	public ObservableSortedSet<K> keySet() {
		return (ObservableSortedSet<K>) super.keySet();
	}

	@Override
	public ObservableSortedSet<ObservableEntry<K, V>> observeEntries() {
		return (ObservableSortedSet<ObservableEntry<K, V>>) super.observeEntries();
	}
}
