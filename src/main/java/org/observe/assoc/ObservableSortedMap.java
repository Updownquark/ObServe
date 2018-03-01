package org.observe.assoc;

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.function.Consumer;

import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;
import org.qommons.Transaction;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableMapEntryHandle;
import org.qommons.collect.SimpleMapEntry;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ObservableSet} that also implements {@link NavigableMap}
 *
 * @param <K> The key type for the map
 * @param <V> The value type for the map
 */
public interface ObservableSortedMap<K, V> extends ObservableMap<K, V>, BetterSortedMap<K, V> {
	@Override
	ObservableSortedSet<K> keySet();

	@Override
	default ObservableSortedSet<Map.Entry<K, V>> observeEntries() {
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
		return entrySet().lower(new SimpleMapEntry<>(key, (V) null));
	}

	@Override
	default K lowerKey(K key) {
		return keySet().lower(key);
	}

	@Override
	default Entry<K, V> floorEntry(K key) {
		return entrySet().floor(new SimpleMapEntry<>(key, (V) null));
	}

	@Override
	default K floorKey(K key) {
		return keySet().floor(key);
	}

	@Override
	default Entry<K, V> ceilingEntry(K key) {
		return entrySet().ceiling(new SimpleMapEntry<>(key, (V) null));
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
			public boolean isLockSupported() {
				return outer.isLockSupported();
			}

			@Override
			public Transaction lock(boolean write, boolean structural, Object cause) {
				return outer.lock(write, structural, cause);
			}

			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<V> getValueType() {
				return outer.getValueType();
			}

			@Override
			public TypeToken<Map.Entry<K, V>> getEntryType() {
				return outer.getEntryType();
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
			public ObservableSortedSet<Map.Entry<K, V>> observeEntries() {
				return outer.observeEntries().descendingSet();
			}

			@Override
			public MapEntryHandle<K, V> putEntry(K key, V value, boolean first) {
				return MapEntryHandle.reverse(outer.putEntry(key, value, !first));
			}

			@Override
			public MapEntryHandle<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
				return MapEntryHandle.reverse(outer.putEntry(key, value, ElementId.reverse(before), ElementId.reverse(after), !first));
			}

			@Override
			public MapEntryHandle<K, V> getEntry(K key) {
				return MapEntryHandle.reverse(outer.getEntry(key));
			}

			@Override
			public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
				return MapEntryHandle.reverse(outer.getEntryById(entryId.reverse()));
			}

			@Override
			public MapEntryHandle<K, V> search(Comparable<? super K> search, SortedSearchFilter filter) {
				return MapEntryHandle.reverse(outer.search(v -> -search.compareTo(v), filter.opposite()));
			}

			@Override
			public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
				return MutableMapEntryHandle.reverse(outer.mutableEntry(entryId.reverse()));
			}

			@Override
			public V put(K key, V value) {
				return outer.put(key, value);
			}

			@Override
			public void putAll(Map<? extends K, ? extends V> m) {
				outer.putAll(m);
			}

			@Override
			public V remove(Object key) {
				return outer.remove(key);
			}

			@Override
			public void clear() {
				outer.clear();
			}

			@Override
			public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
				try (Transaction t = lock(false, null)) {
					int[] size = new int[] { size() };
					return outer.onChange(evt -> {
						if (evt.getType() == CollectionChangeType.add)
							size[0]++;
						int index = size[0] - evt.getIndex() - 1;
						if (evt.getType() == CollectionChangeType.remove)
							size[0]--;
						ObservableMapEvent<K, V> mapEvent = new ObservableMapEvent<>(evt.getKeyElement().reverse(),
							evt.getElementId().reverse(), outer.getKeyType(), outer.getValueType(), index, index, evt.getType(),
							evt.getKey(), evt.getOldValue(), evt.getNewValue(), evt);
						try (Transaction mt = ObservableMapEvent.use(mapEvent)) {
							action.accept(mapEvent);
						}
					});
				}
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

	/**
	 * @param from Determines the minimum key to use
	 * @param to Determines the maximum key to use
	 * @return A sorted sub-map containing only the specified range of keys
	 */
	@Override
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
	class ObservableSubMap<K, V> extends BetterSortedMap.BetterSubMap<K, V> implements ObservableSortedMap<K, V> {
		public ObservableSubMap(ObservableSortedMap<K, V> source, Comparable<? super K> lower, Comparable<? super K> upper) {
			super(source, lower, upper);
		}

		@Override
		protected ObservableSortedMap<K, V> getSource() {
			return (ObservableSortedMap<K, V>) super.getSource();
		}

		@Override
		public boolean isLockSupported() {
			return getSource().isLockSupported();
		}

		@Override
		public TypeToken<K> getKeyType() {
			return getSource().getKeyType();
		}

		@Override
		public TypeToken<V> getValueType() {
			return getSource().getValueType();
		}

		@Override
		public TypeToken<Map.Entry<K, V>> getEntryType() {
			return getSource().getEntryType();
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return getSource().equivalence();
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			return getSource().onChange(evt -> {
				if (!keySet().belongs(evt.getKey()))
					return;
				int index = keySet().getElementsBefore(evt.getKeyElement());
				ObservableMapEvent<K, V> mapEvent = new ObservableMapEvent<>(evt.getKeyElement(), evt.getElementId(),
					getSource().getKeyType(), getSource().getValueType(), index, index, evt.getType(), evt.getKey(), evt.getOldValue(),
					evt.getNewValue(), evt);
				try (Transaction t = ObservableMapEvent.use(mapEvent)) {
					action.accept(mapEvent);
				}
			});
		}
	};
}
