package org.observe.assoc;

import static org.observe.assoc.ObservableMap.ObservableEntry.constEntry;

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;

import org.observe.ObservableValue;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;
import org.qommons.SimpleMapEntry;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ObservableSet} that also implements {@link NavigableMap}
 *
 * @param <K> The key type for the map
 * @param <V> The value type for the map
 */
public interface ObservableSortedMap<K, V> extends ObservableMap<K, V>, NavigableMap<K, V> {
	@Override
	ObservableSortedSet<K> keySet();

	/**
	 * <p>
	 * A default implementation of {@link #keySet()}.
	 * </p>
	 * <p>
	 * No {@link ObservableMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #observeEntries()} methods. {@link #defaultObserveEntries(ObservableSortedMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(ObservableSortedMap)} or {@link ObservableMap#defaultObserve(ObservableMap, Object)}. Either
	 * {@link #observeEntries()} or both {@link #keySet()} and {@link #get(Object)} must be custom. If an implementation supplies custom
	 * {@link #keySet()} and {@link #get(Object)} implementations, it may use {@link #defaultObserveEntries(ObservableSortedMap)} for its
	 * {@link #observeEntries()} . If an implementation supplies a custom {@link #observeEntries()} implementation, it may use
	 * {@link #defaultKeySet(ObservableSortedMap)} and {@link ObservableMap#defaultObserve(ObservableMap, Object)} for its {@link #keySet()}
	 * and {@link #get(Object)} implementations, respectively. Using default implementations for both will result in infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create a key set for
	 * @return A key set for the map
	 */
	public static <K, V> ObservableSortedSet<K> defaultKeySet(ObservableSortedMap<K, V> map) {
		return ObservableSortedSet.<K> unique(map.observeEntries().map(Entry::getKey), map.comparator());
	}

	@Override
	ObservableSortedSet<? extends ObservableEntry<K, V>> observeEntries();

	/**
	 * <p>
	 * A default implementation of {@link #observeEntries()}.
	 * </p>
	 * <p>
	 * No {@link ObservableMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #observeEntries()} methods. {@link #defaultObserveEntries(ObservableSortedMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(ObservableSortedMap)} or {@link ObservableMap#defaultObserve(ObservableMap, Object)}. Either
	 * {@link #observeEntries()} or both {@link #keySet()} and {@link #get(Object)} must be custom. If an implementation supplies custom
	 * {@link #keySet()} and {@link #get(Object)} implementations, it may use {@link #defaultObserveEntries(ObservableSortedMap)} for its
	 * {@link #observeEntries()} . If an implementation supplies a custom {@link #observeEntries()} implementation, it may use
	 * {@link #defaultKeySet(ObservableSortedMap)} and {@link ObservableMap#defaultObserve(ObservableMap, Object)} for its {@link #keySet()}
	 * and {@link #get(Object)} implementations, respectively. Using default implementations for both will result in infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create an entry set for
	 * @return An entry set for the map
	 */
	public static <K, V> ObservableSortedSet<? extends ObservableEntry<K, V>> defaultObserveEntries(ObservableSortedMap<K, V> map) {
		return ObservableSortedSet.unique(map.keySet().map(map::entryFor),
				(Entry<K, V> entry1, Entry<K, V> entry2) -> map.comparator().compare(entry1.getKey(), entry2.getKey()));
	}

	@Override
	default ObservableCollection<V> values() {
		return ObservableMap.super.values();
	}

	@Override
	default ObservableSortedSet<Entry<K, V>> entrySet() {
		return (ObservableSortedSet<Entry<K, V>>) (ObservableSet<?>) observeEntries();
	}

	@Override
	default Comparator<? super K> comparator() {
		return keySet().comparator();
	}

	@Override
	default K firstKey() {
		return keySet().first();
	}

	@Override
	default K lastKey() {
		return keySet().last();
	}

	@Override
	default Entry<K, V> lowerEntry(K key) {
		return entrySet().lower(constEntry(getValueType(), key, (V) null));
	}

	@Override
	default K lowerKey(K key) {
		return keySet().lower(key);
	}

	@Override
	default Entry<K, V> floorEntry(K key) {
		return entrySet().floor(constEntry(getValueType(), key, (V) null));
	}

	@Override
	default K floorKey(K key) {
		return keySet().floor(key);
	}

	@Override
	default Entry<K, V> ceilingEntry(K key) {
		return entrySet().ceiling(constEntry(getValueType(), key, (V) null));
	}

	@Override
	default K ceilingKey(K key) {
		return keySet().ceiling(key);
	}

	@Override
	default Entry<K, V> higherEntry(K key) {
		return entrySet().higher(new SimpleMapEntry<>(key, (V) null));
	}

	@Override
	default K higherKey(K key) {
		return keySet().higher(key);
	}

	@Override
	default Entry<K, V> firstEntry() {
		return entrySet().first();
	}

	@Override
	default Entry<K, V> lastEntry() {
		return entrySet().last();
	}

	@Override
	default Entry<K, V> pollFirstEntry() {
		return entrySet().pollFirst();
	}

	@Override
	default Entry<K, V> pollLastEntry() {
		return entrySet().pollLast();
	}

	@Override
	default ObservableSortedMap<K, V> descendingMap() {
		ObservableSortedMap<K, V> outer = this;
		return new ObservableSortedMap<K, V>() {
			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<V> getValueType() {
				return outer.getValueType();
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
			}

			@Override
			public boolean isSafe() {
				return outer.isSafe();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public ObservableSortedSet<K> keySet() {
				return outer.keySet().descendingSet();
			}

			@Override
			public ObservableValue<V> observe(Object key) {
				return outer.observe(key);
			}

			@Override
			public ObservableCollection<V> values() {
				return outer.values();
			}

			@Override
			public ObservableSortedSet<? extends ObservableEntry<K, V>> observeEntries() {
				return outer.observeEntries().descendingSet();
			}

			@Override
			public V put(K key, V value) {
				return outer.put(key, value);
			}

			@Override
			public V remove(Object key) {
				return outer.remove(key);
			}

			@Override
			public void putAll(Map<? extends K, ? extends V> m) {
				outer.putAll(m);
			}

			@Override
			public void clear() {
				outer.clear();
			}

			@Override
			public ObservableSortedMap<K, V> descendingMap() {
				return outer;
			}

			@Override
			public ObservableSortedSet<K> descendingKeySet() {
				return outer.keySet();
			}
		};
	}

	@Override
	default ObservableSortedSet<K> navigableKeySet() {
		return keySet();
	}

	@Override
	default ObservableSortedSet<K> descendingKeySet() {
		return keySet().descendingSet();
	}

	@Override
	default ObservableSortedMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		return new ObservableSubMap<>(this, true, fromKey, fromInclusive, true, toKey, toInclusive);
	}

	@Override
	default ObservableSortedMap<K, V> headMap(K toKey, boolean inclusive) {
		return new ObservableSubMap<>(this, false, null, false, true, toKey, inclusive);
	}

	@Override
	default ObservableSortedMap<K, V> tailMap(K fromKey, boolean inclusive) {
		return new ObservableSubMap<>(this, true, fromKey, inclusive, false, null, false);
	}

	@Override
	default ObservableSortedMap<K, V> subMap(K fromKey, K toKey) {
		return new ObservableSubMap<>(this, true, fromKey, true, true, toKey, true);
	}

	@Override
	default ObservableSortedMap<K, V> headMap(K toKey) {
		return new ObservableSubMap<>(this, false, null, false, true, toKey, true);
	}

	@Override
	default ObservableSortedMap<K, V> tailMap(K fromKey) {
		return new ObservableSubMap<>(this, true, fromKey, true, false, null, false);
	}

	/**
	 * Implements {@link ObservableSortedMap#subMap(Object, boolean, Object, boolean)}
	 * 
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ObservableSubMap<K, V> implements ObservableSortedMap<K, V> {
		private final ObservableSortedMap<K, V> theOuter;
		private boolean hasLowerBound;
		private K theLowerBound;
		private boolean isLowerInclusive;
		private boolean hasUpperBound;
		private K theUpperBound;
		private boolean isUpperInclusive;

		public ObservableSubMap(ObservableSortedMap<K, V> outer, boolean hasLower, K lowerBound, boolean lowerInclusive, boolean hasUpper,
				K upperBound, boolean upperInclusive) {
			theOuter = outer;
			hasLowerBound = hasLower;
			theLowerBound = lowerBound;
			isLowerInclusive = lowerInclusive;
			hasUpperBound = hasUpper;
			theUpperBound = upperBound;
			isUpperInclusive = upperInclusive;
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theOuter.getKeyType();
		}

		@Override
		public TypeToken<V> getValueType() {
			return theOuter.getValueType();
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theOuter.getSession();
		}

		@Override
		public boolean isSafe() {
			return theOuter.isSafe();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theOuter.lock(write, cause);
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			if (hasLowerBound) {
				if (hasUpperBound)
					return theOuter.keySet().subSet(theLowerBound, isLowerInclusive, theUpperBound, isUpperInclusive);
				else
					return theOuter.keySet().tailSet(theLowerBound, isLowerInclusive);
			} else
				return theOuter.keySet().headSet(theUpperBound, isUpperInclusive);
		}

		@Override
		public ObservableValue<V> observe(Object key) {
			if (!getKeyType().getRawType().isInstance(key))
				return ObservableValue.constant(getValueType(), null);
			if (hasLowerBound) {
				int lowCompare = theOuter.comparator().compare((K) key, theLowerBound);
				if (lowCompare < 0 || (lowCompare == 0 && !isLowerInclusive))
					return ObservableValue.constant(getValueType(), null);
			}
			if (hasUpperBound) {
				int highCompare = theOuter.comparator().compare((K) key, theUpperBound);
				if (highCompare > 0 || (highCompare == 0 && !isUpperInclusive))
					return ObservableValue.constant(getValueType(), null);
			}
			return theOuter.observe(key);
		}

		@Override
		public ObservableSortedSet<? extends ObservableEntry<K, V>> observeEntries() {
			ObservableSortedSet<ObservableEntry<K, V>> entries = (ObservableSortedSet<ObservableEntry<K, V>>) theOuter.observeEntries();
			if (hasLowerBound) {
				if (hasUpperBound)
					return entries.subSet(constEntry(getValueType(), theLowerBound, (V) null), isLowerInclusive,
							constEntry(getValueType(), theUpperBound, null), isUpperInclusive);
				else
					return entries.tailSet(constEntry(getValueType(), theLowerBound, (V) null), isLowerInclusive);
			} else
				return entries.headSet(constEntry(getValueType(), theUpperBound, (V) null), isUpperInclusive);
		}
	};
}
