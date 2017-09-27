package org.observe.assoc;

import static org.observe.assoc.ObservableMap.ObservableEntry.constEntry;

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;

import org.observe.ObservableValue;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;
import org.qommons.Transaction;
import org.qommons.collect.SimpleMapEntry;

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

	@Override
	default ObservableSortedSet<ObservableEntry<K, V>> observeEntries() {
		return keySet().flow().mapEquivalent(getEntryType(), this::entryFor, entry -> entry.getKey(), options -> options.cache(false))
			.collect();
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
			public TypeToken<ObservableEntry<K, V>> getEntryType() {
				return outer.getEntryType();
			}

			@Override
			public boolean isLockSupported() {
				return outer.isLockSupported();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public Equivalence<? super V> equivalence() {
				return outer.equivalence();
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
			public ObservableSortedSet<ObservableEntry<K, V>> observeEntries() {
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

	default ObservableSortedMap<K, V> subMap(Comparable<? super K> from, Comparable<? super K> to) {
		return new ObservableSubMap<>(this, from, to);
	}

	@Override
	default ObservableSortedMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		return subMap(v -> {
			int comp = comparator().compare(toKey, v);
			if (!fromInclusive && comp == 0)
				comp = 1;
			return comp;
		}, v -> {
			int comp = comparator().compare(toKey, v);
			if (!fromInclusive && comp == 0)
				comp = -1;
			return comp;
		});
	}

	@Override
	default ObservableSortedMap<K, V> headMap(K toKey, boolean inclusive) {
		return subMap(null, v -> {
			int comp = comparator().compare(toKey, v);
			if (!inclusive && comp == 0)
				comp = -1;
			return comp;
		});
	}

	@Override
	default ObservableSortedMap<K, V> tailMap(K fromKey, boolean inclusive) {
		return subMap(v -> {
			int comp = comparator().compare(fromKey, v);
			if (!inclusive && comp == 0)
				comp = 1;
			return comp;
		}, null);
	}

	@Override
	default ObservableSortedMap<K, V> subMap(K fromKey, K toKey) {
		return subMap(fromKey, true, toKey, true);
	}

	@Override
	default ObservableSortedMap<K, V> headMap(K toKey) {
		return headMap(toKey, true);
	}

	@Override
	default ObservableSortedMap<K, V> tailMap(K fromKey) {
		return tailMap(fromKey, true);
	}

	/**
	 * Implements {@link ObservableSortedMap#subMap(Object, boolean, Object, boolean)}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ObservableSubMap<K, V> implements ObservableSortedMap<K, V> {
		private final ObservableSortedMap<K, V> theOuter;
		private final Comparable<? super K> theLower;
		private final Comparable<? super K> theUpper;

		public ObservableSubMap(ObservableSortedMap<K, V> outer, Comparable<? super K> lower, Comparable<? super K> upper) {
			theOuter = outer;
			theLower = lower;
			theUpper = upper;
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
		public TypeToken<ObservableEntry<K, V>> getEntryType() {
			return theOuter.getEntryType();
		}

		@Override
		public boolean isLockSupported() {
			return theOuter.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theOuter.lock(write, cause);
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return theOuter.equivalence();
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return theOuter.keySet().subSet(theLower, theUpper);
		}

		@Override
		public ObservableValue<V> observe(Object key) {
			if (!keySet().belongs(key))
				return ObservableValue.constant(getValueType(), null);
			return theOuter.observe(key);
		}
	};
}
