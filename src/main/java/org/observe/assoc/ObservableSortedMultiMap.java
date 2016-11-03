package org.observe.assoc;

import java.util.Collection;
import java.util.Comparator;

import org.observe.ObservableValue;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableList;
import org.observe.collect.ObservableOrderedCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;
import org.qommons.Transaction;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A sorted {@link ObservableMultiMap}
 *
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public interface ObservableSortedMultiMap<K, V> extends ObservableMultiMap<K, V> {
	/**
	 * A multi-map entry that can be sorted
	 *
	 * @param <K> The type of the key
	 * @param <V> The type of the value
	 */
	interface ObservableSortedMultiEntry<K, V> extends ObservableMultiEntry<K, V>, Comparable<ObservableSortedMultiEntry<K, V>> {}

	/** @return The comparator by which this map's keys are sorted */
	default Comparator<? super K> comparator() {
		return keySet().comparator();
	}

	/**
	 * @param key The key to get the entry for
	 * @return A multi-entry that represents the given key's presence in this map. Never null.
	 */
	@Override
	default ObservableSortedMultiEntry<K, V> entryFor(K key) {
		ObservableCollection<V> values = get(key);
		if (values instanceof ObservableSortedMultiEntry)
			return (ObservableSortedMultiEntry<K, V>) values;
		else if (values instanceof ObservableList)
			return new ObsSortedMultiEntryList<>(this, key, (ObservableList<V>) values, comparator());
		else if (values instanceof ObservableSortedSet)
			return new ObsSortedMultiEntrySortedSet<>(this, key, (ObservableSortedSet<V>) values, comparator());
		else if (values instanceof ObservableOrderedCollection)
			return new ObsSortedMultiEntryOrdered<>(this, key, (ObservableOrderedCollection<V>) values, comparator());
		else if (values instanceof ObservableSet)
			return new ObsSortedMultiEntrySet<>(this, key, (ObservableSet<V>) values, comparator());
		else
			return new ObsSortedMultiEntryImpl<>(this, key, values, comparator());
	}

	@Override
	default ObservableMultiEntry<K, V> entryFor(K key, ObservableValue<? extends ObservableMultiEntry<K, V>> values) {
		if (values.getType().isAssignableFrom(ObservableList.class)) {
			return new ObsSortedMultiEntryList<>(this, key, getValueType(), (ObservableValue<? extends ObservableList<V>>) values,
				comparator());
		} else if (values.getType().isAssignableFrom(ObservableSortedSet.class)) {
			return new ObsSortedMultiEntrySortedSet<>(this, key, getValueType(), (ObservableValue<? extends ObservableSortedSet<V>>) values,
				comparator());
		} else if (values.getType().isAssignableFrom(ObservableOrderedCollection.class)) {
			return new ObsSortedMultiEntryOrdered<>(this, key, getValueType(),
				(ObservableValue<? extends ObservableOrderedCollection<V>>) values, comparator());
		} else if (values.getType().isAssignableFrom(ObservableSet.class)) {
			return new ObsSortedMultiEntrySet<>(this, key, getValueType(), (ObservableValue<? extends ObservableSet<V>>) values,
				comparator());
		} else {
			return new ObsSortedMultiEntryImpl<>(this, key, getValueType(), values, comparator());
		}
	}

	@Override
	ObservableSortedSet<K> keySet();

	/**
	 * <p>
	 * A default implementation of {@link #keySet()}.
	 * </p>
	 * <p>
	 * No {@link ObservableMultiMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #entrySet()} methods. {@link #defaultEntrySet(ObservableSortedMultiMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(ObservableSortedMultiMap)} or {@link ObservableMultiMap#defaultGet(ObservableMultiMap, Object)}. Either
	 * {@link #entrySet()} or both {@link #keySet()} and {@link #get(Object)} must be custom. If an implementation supplies custom
	 * {@link #keySet()} and {@link #get(Object)} implementations, it may use {@link #defaultEntrySet(ObservableSortedMultiMap)} for its
	 * {@link #entrySet()} . If an implementation supplies a custom {@link #entrySet()} implementation, it may use
	 * {@link #defaultKeySet(ObservableSortedMultiMap)} and {@link ObservableMultiMap#defaultGet(ObservableMultiMap, Object)} for its
	 * {@link #keySet()} and {@link #get(Object)} implementations, respectively. Using default implementations for both will result in
	 * infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create a key set for
	 * @return A key set for the map
	 */
	public static <K, V> ObservableSortedSet<K> defaultKeySet(ObservableSortedMultiMap<K, V> map) {
		return ObservableSortedSet.unique(map.entrySet().map(ObservableMultiEntry::getKey), map.comparator());
	}

	@Override
	ObservableSortedSet<? extends ObservableSortedMultiEntry<K, V>> entrySet();

	/**
	 * <p>
	 * A default implementation of {@link #entrySet()}.
	 * </p>
	 * <p>
	 * No {@link ObservableMultiMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #entrySet()} methods. {@link #defaultEntrySet(ObservableSortedMultiMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(ObservableSortedMultiMap)} or {@link ObservableMultiMap#defaultGet(ObservableMultiMap, Object)}. Either
	 * {@link #entrySet()} or both {@link #keySet()} and {@link #get(Object)} must be custom. If an implementation supplies custom
	 * {@link #keySet()} and {@link #get(Object)} implementations, it may use {@link #defaultEntrySet(ObservableSortedMultiMap)} for its
	 * {@link #entrySet()} . If an implementation supplies a custom {@link #entrySet()} implementation, it may use
	 * {@link #defaultKeySet(ObservableSortedMultiMap)} and {@link ObservableMultiMap#defaultGet(ObservableMultiMap, Object)} for its
	 * {@link #keySet()} and {@link #get(Object)} implementations, respectively. Using default implementations for both will result in
	 * infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create an entry set for
	 * @return An entry set for the map
	 */
	public static <K, V> ObservableSortedSet<? extends ObservableSortedMultiEntry<K, V>> defaultEntrySet(
		ObservableSortedMultiMap<K, V> map) {
		return ObservableSortedSet.unique(map.keySet().map(map::entryFor),
			(entry1, entry2) -> map.keySet().comparator().compare(entry1.getKey(), entry2.getKey()));
	}

	@Override
	default ObservableSortedMap<K, Collection<V>> asCollectionMap() {
		ObservableSortedMultiMap<K, V> outer = this;
		class SortedCollectionMap implements ObservableSortedMap<K, Collection<V>> {
			private TypeToken<Collection<V>> theValueType = new TypeToken<Collection<V>>() {}.where(new TypeParameter<V>() {},
				outer.getValueType());

			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<Collection<V>> getValueType() {
				return theValueType;
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public boolean isSafe() {
				return outer.isSafe();
			}

			@Override
			public ObservableSortedSet<K> keySet() {
				return outer.keySet();
			}

			@Override
			public ObservableValue<Collection<V>> observe(Object key) {
				return outer.get(key).asValue();
			}

			@Override
			public ObservableSortedSet<? extends ObservableEntry<K, Collection<V>>> observeEntries() {
				return ObservableSortedMap.defaultObserveEntries(this);
			}
		}
		return new SortedCollectionMap();
	}

	/**
	 * Simple multi-entry implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsSortedMultiEntryImpl<K, V> extends ObsMultiEntryImpl<K, V> implements ObservableSortedMultiEntry<K, V> {
		private final Comparator<? super K> theComparator;

		ObsSortedMultiEntryImpl(ObservableSortedMultiMap<K, V> map, K key, ObservableCollection<V> values,
			Comparator<? super K> comparator) {
			super(map, key, values);
			theComparator = comparator;
		}

		ObsSortedMultiEntryImpl(ObservableSortedMultiMap<K, V> map, K key, TypeToken<V> valueType,
			ObservableValue<? extends ObservableCollection<V>> values, Comparator<? super K> comparator) {
			super(map, key, valueType, values);
			theComparator = comparator;
		}

		@Override
		protected ObservableSortedMultiMap<K, V> getMap() {
			return (ObservableSortedMultiMap<K, V>) super.getMap();
		}

		@Override
		public int compareTo(ObservableSortedMultiEntry<K, V> o) {
			return theComparator.compare(getKey(), o.getKey());
		}
	}

	/**
	 * Simple ordered multi-entry implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsSortedMultiEntryOrdered<K, V> extends ObsMultiEntryOrdered<K, V> implements ObservableSortedMultiEntry<K, V> {
		private final Comparator<? super K> theComparator;

		public ObsSortedMultiEntryOrdered(ObservableMultiMap<K, V> map, K key, ObservableOrderedCollection<V> values,
			Comparator<? super K> comparator) {
			super(map, key, values);
			theComparator = comparator;
		}

		public ObsSortedMultiEntryOrdered(ObservableMultiMap<K, V> map, K key, TypeToken<V> valueType,
			ObservableValue<? extends ObservableOrderedCollection<V>> values, Comparator<? super K> comparator) {
			super(map, key, valueType, values);
			theComparator = comparator;
		}

		@Override
		public int compareTo(ObservableSortedMultiEntry<K, V> o) {
			return theComparator.compare(getKey(), o.getKey());
		}
	}

	/**
	 * Simple multi-entry sorted set implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsSortedMultiEntrySortedSet<K, V> extends ObsMultiEntrySortedSet<K, V> implements ObservableSortedMultiEntry<K, V> {
		private final Comparator<? super K> theComparator;

		public ObsSortedMultiEntrySortedSet(ObservableMultiMap<K, V> map, K key, ObservableSortedSet<V> values,
			Comparator<? super K> comparator) {
			super(map, key, values);
			theComparator = comparator;
		}

		public ObsSortedMultiEntrySortedSet(ObservableMultiMap<K, V> map, K key, TypeToken<V> valueType,
			ObservableValue<? extends ObservableSortedSet<V>> values, Comparator<? super K> comparator) {
			super(map, key, valueType, values);
			theComparator = comparator;
		}

		@Override
		public int compareTo(ObservableSortedMultiEntry<K, V> o) {
			return theComparator.compare(getKey(), o.getKey());
		}
	}

	/**
	 * Simple multi-entry list implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsSortedMultiEntryList<K, V> extends ObsMultiEntryList<K, V> implements ObservableSortedMultiEntry<K, V> {
		private final Comparator<? super K> theComparator;

		public ObsSortedMultiEntryList(ObservableMultiMap<K, V> map, K key, ObservableList<V> values, Comparator<? super K> comparator) {
			super(map, key, values);
			theComparator = comparator;
		}

		public ObsSortedMultiEntryList(ObservableMultiMap<K, V> map, K key, TypeToken<V> valueType,
			ObservableValue<? extends ObservableList<V>> values, Comparator<? super K> comparator) {
			super(map, key, valueType, values);
			theComparator = comparator;
		}

		@Override
		public int compareTo(ObservableSortedMultiEntry<K, V> o) {
			return theComparator.compare(getKey(), o.getKey());
		}
	}

	/**
	 * Simple multi-entry set implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsSortedMultiEntrySet<K, V> extends ObsMultiEntrySet<K, V> implements ObservableSortedMultiEntry<K, V> {
		private final Comparator<? super K> theComparator;

		public ObsSortedMultiEntrySet(ObservableMultiMap<K, V> map, K key, ObservableSet<V> values, Comparator<? super K> comparator) {
			super(map, key, values);
			theComparator = comparator;
		}

		public ObsSortedMultiEntrySet(ObservableMultiMap<K, V> map, K key, TypeToken<V> valueType,
			ObservableValue<? extends ObservableSet<V>> values, Comparator<? super K> comparator) {
			super(map, key, valueType, values);
			theComparator = comparator;
		}

		@Override
		public int compareTo(ObservableSortedMultiEntry<K, V> o) {
			return theComparator.compare(getKey(), o.getKey());
		}
	}
}
