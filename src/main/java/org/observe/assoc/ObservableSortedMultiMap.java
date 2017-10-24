package org.observe.assoc;

import java.util.Comparator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.Subscription;
import org.observe.collect.Equivalence;
import org.observe.collect.FlowOptions.GroupingOptions;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.UniqueSortedDataFlow;
import org.observe.collect.ObservableSortedSet;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeToken;

/**
 * A sorted {@link ObservableMultiMap}
 *
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public interface ObservableSortedMultiMap<K, V> extends ObservableMultiMap<K, V> {
	@Override
	ObservableSortedSet<K> keySet();

	@Override
	default ObservableSortedSet<ObservableMultiEntry<K, V>> entrySet() {
		return keySet().flow().mapEquivalent(getEntryType(), this::entryFor, entry -> entry.getKey(), options -> options.cache(false))
			.collect();
	}

	default ObservableMultiEntry<K, V> lowerEntry(K key) {
		return entrySet().lower(ObservableMultiEntry.empty(getKeyType(), getValueType(), key, keySet().equivalence()));
	}

	default ObservableMultiEntry<K, V> floorEntry(K key) {
		return entrySet().floor(ObservableMultiEntry.empty(getKeyType(), getValueType(), key, keySet().equivalence()));
	}

	default ObservableMultiEntry<K, V> ceilingEntry(K key) {
		return entrySet().ceiling(ObservableMultiEntry.empty(getKeyType(), getValueType(), key, keySet().equivalence()));
	}

	default ObservableMultiEntry<K, V> higherEntry(K key) {
		return entrySet().higher(ObservableMultiEntry.empty(getKeyType(), getValueType(), key, keySet().equivalence()));
	}

	default ObservableSortedMultiMap<K, V> reverse() {
		ObservableSortedMultiMap<K, V> outer = this;
		return new ObservableSortedMultiMap<K, V>() {
			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<V> getValueType() {
				return outer.getValueType();
			}

			@Override
			public TypeToken<ObservableMultiEntry<K, V>> getEntryType() {
				return outer.getEntryType();
			}

			@Override
			public boolean isLockSupported() {
				return outer.isLockSupported();
			}

			@Override
			public Transaction lock(boolean write, boolean structural, Object cause) {
				return outer.lock(write, structural, cause);
			}

			@Override
			public ObservableSortedSet<K> keySet() {
				return outer.keySet().reverse();
			}

			@Override
			public ObservableCollection<V> get(Object key) {
				return outer.get(key);
			}

			@Override
			public ObservableCollection<V> values() {
				return outer.values();
			}

			@Override
			public ObservableSortedSet<ObservableMultiEntry<K, V>> entrySet() {
				return outer.entrySet().reverse();
			}

			@Override
			public SortedMultiMapFlow<K, V> flow() {
				return outer.flow().reverse();
			}

			@Override
			public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
				try (Transaction t = lock(false, null)) {
					return outer.onChange(evt -> {
						int keySize = keySet().size();
						if (keySize == 0)
							keySize++; // May have just been removed
						int keyIndex = keySize - evt.getKeyIndex() - 1;
						int valueSize = get(evt.getKey()).size();
						if (valueSize == 0)
							valueSize++; // May have just been removed
						int valueIndex = valueSize - evt.getIndex() - 1;
						ObservableMapEvent.doWith(new ObservableMapEvent<>(evt.getKeyElement().reverse(), evt.getElementId().reverse(),
							outer.getKeyType(), outer.getValueType(), keyIndex, valueIndex, evt.getType(), evt.getKey(), evt.getOldValue(),
							evt.getNewValue(), evt), action);
					});
				}
			}

			@Override
			public ObservableSortedMultiMap<K, V> reverse() {
				return outer;
			}

			@Override
			public String toString() {
				return ObservableMultiMap.toString(ObservableSortedMultiMap.this);
			}
		};
	}

	default ObservableSortedMultiMap<K, V> subMap(Comparable<? super K> from, Comparable<? super K> to) {
		return new ObservableSubMultiMap<>(this, from, to);
	}

	default ObservableSortedMultiMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		return subMap(v -> {
			int comp = keySet().comparator().compare(toKey, v);
			if (!fromInclusive && comp == 0)
				comp = 1;
			return comp;
		}, v -> {
			int comp = keySet().comparator().compare(toKey, v);
			if (!fromInclusive && comp == 0)
				comp = -1;
			return comp;
		});
	}

	default ObservableSortedMultiMap<K, V> headMap(K toKey, boolean inclusive) {
		return subMap(null, v -> {
			int comp = keySet().comparator().compare(toKey, v);
			if (!inclusive && comp == 0)
				comp = -1;
			return comp;
		});
	}

	default ObservableSortedMultiMap<K, V> tailMap(K fromKey, boolean inclusive) {
		return subMap(v -> {
			int comp = keySet().comparator().compare(fromKey, v);
			if (!inclusive && comp == 0)
				comp = 1;
			return comp;
		}, null);
	}

	default ObservableSortedMultiMap<K, V> subMap(K fromKey, K toKey) {
		return subMap(fromKey, true, toKey, true);
	}

	default ObservableSortedMultiMap<K, V> headMap(K toKey) {
		return headMap(toKey, true);
	}

	default ObservableSortedMultiMap<K, V> tailMap(K fromKey) {
		return tailMap(fromKey, true);
	}

	@Override
	SortedMultiMapFlow<K, V> flow();

	@Override
	default ObservableSortedMap<K, V> unique(){
		return new UniqueSortedMap<>(this);
	}

	static <K, V> SortedMultiMapFlow<K, V> create(TypeToken<K> keyType, TypeToken<V> valueType, Comparator<? super K> keyCompare) {
		return create(keyType, valueType, keyCompare, ObservableCollection.createDefaultBacking());
	}

	static <K, V> SortedMultiMapFlow<K, V> create(TypeToken<K> keyType, TypeToken<V> valueType, Comparator<? super K> keyCompare,
		BetterList<Map.Entry<K, V>> entryCollection) {
		return (SortedMultiMapFlow<K, V>) ObservableMultiMap.create(keyType, valueType,
			Equivalence.of((Class<K>) keyType.getRawType(), keyCompare, true));
	}

	interface SortedMultiMapFlow<K, V> extends MultiMapFlow<K, V> {
		<K2> SortedMultiMapFlow<K2, V> withSortedKeys(Function<UniqueSortedDataFlow<?, ?, K>, UniqueSortedDataFlow<?, ?, K2>> keyMap);

		@Override
		<V2> SortedMultiMapFlow<K, V2> withValues(Function<CollectionDataFlow<?, ?, V>, CollectionDataFlow<?, ?, V2>> valueMap);

		@Override
		SortedMultiMapFlow<K, V> distinctForMap();

		@Override
		SortedMultiMapFlow<K, V> distinctForMap(Consumer<UniqueOptions> options);

		SortedMultiMapFlow<K, V> reverse();

		@Override
		default ObservableSortedMultiMap<K, V> gather() {
			return gather(options -> {});
		}

		@Override
		default ObservableSortedMultiMap<K, V> gather(Consumer<GroupingOptions> options) {
			return gather(Observable.empty, options);
		}

		@Override
		default ObservableSortedMultiMap<K, V> gather(Observable<?> until) {
			return gather(until, options -> {});
		}

		@Override
		ObservableSortedMultiMap<K, V> gather(Observable<?> until, Consumer<GroupingOptions> options);
	}

	/**
	 * Implements {@link ObservableSortedMap#subMap(Object, boolean, Object, boolean)}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ObservableSubMultiMap<K, V> implements ObservableSortedMultiMap<K, V> {
		private final ObservableSortedMultiMap<K, V> theOuter;
		private final Comparable<? super K> theLower;
		private final Comparable<? super K> theUpper;

		public ObservableSubMultiMap(ObservableSortedMultiMap<K, V> outer, Comparable<? super K> lower, Comparable<? super K> upper) {
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
		public TypeToken<ObservableMultiEntry<K, V>> getEntryType() {
			return theOuter.getEntryType();
		}

		@Override
		public boolean isLockSupported() {
			return theOuter.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theOuter.lock(write, structural, cause);
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return theOuter.keySet().subSet(theLower, theUpper);
		}

		@Override
		public ObservableCollection<V> get(Object key) {
			if (!keySet().belongs(key))
				return ObservableCollection.of(getValueType());
			return theOuter.get(key);
		}

		@Override
		public SortedMultiMapFlow<K, V> flow() {
			return theOuter.flow().withSortedKeys(keys -> keys.filter(k -> {
				if (theLower != null && theLower.compareTo(k) > 0)
					return StdMsg.ILLEGAL_ELEMENT;
				if (theUpper != null && theUpper.compareTo(k) < 0)
					return StdMsg.ILLEGAL_ELEMENT;
				return null;
			}));
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			return theOuter.onChange(evt -> {
				if (!keySet().belongs(evt.getKey()))
					return;
				int keyIndex = keySet().getElementsBefore(evt.getKeyElement());
				int valueIndex = get(evt.getKey()).getElementsBefore(evt.getElementId());
				ObservableMapEvent.doWith(new ObservableMapEvent<>(evt.getKeyElement(), evt.getElementId(), theOuter.getKeyType(),
					theOuter.getValueType(), keyIndex, valueIndex, evt.getType(), evt.getKey(), evt.getOldValue(), evt.getNewValue(), evt),
					action);
			});
		}

		@Override
		public String toString() {
			return ObservableMultiMap.toString(this);
		}
	}

	class UniqueSortedMap<K, V> extends UniqueMap<K, V> implements ObservableSortedMap<K, V> {
		public UniqueSortedMap(ObservableSortedMultiMap<K, V> outer) {
			super(outer);
		}

		@Override
		protected ObservableSortedMultiMap<K, V> getOuter() {
			return (ObservableSortedMultiMap<K, V>) super.getOuter();
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}
	}
}
