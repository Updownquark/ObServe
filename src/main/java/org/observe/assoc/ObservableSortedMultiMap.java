package org.observe.assoc;

import java.util.Comparator;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
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
import org.qommons.collect.BetterSortedMultiMap;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeToken;

/**
 * A sorted {@link ObservableMultiMap}
 *
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public interface ObservableSortedMultiMap<K, V> extends ObservableMultiMap<K, V>, BetterSortedMultiMap<K, V> {
	@Override
	Comparator<? super K> comparator();

	@Override
	ObservableSortedSet<K> keySet();

	@Override
	default ObservableSortedSet<? extends ObservableMultiEntry<K, V>> entrySet() {
		return (ObservableSortedSet<? extends ObservableMultiEntry<K, V>>) ObservableMultiMap.super.entrySet();
	}

	@Override
	default ObservableMultiEntry<K, V> search(Comparable<? super K> search, SortedSearchFilter filter) {
		CollectionElement<K> keyEl = keySet().search(search, filter);
		return keyEl == null ? null : getEntry(keyEl.getElementId());
	}

	@Override
	default ObservableMultiEntry<K, V> firstEntry() {
		return (ObservableMultiEntry<K, V>) BetterSortedMultiMap.super.firstEntry();
	}

	@Override
	default ObservableMultiEntry<K, V> lastEntry() {
		return (ObservableMultiEntry<K, V>) BetterSortedMultiMap.super.lastEntry();
	}

	@Override
	default ObservableMultiEntry<K, V> lowerEntry(K key) {
		return (ObservableMultiEntry<K, V>) BetterSortedMultiMap.super.lowerEntry(key);
	}

	@Override
	default ObservableMultiEntry<K, V> floorEntry(K key) {
		return (ObservableMultiEntry<K, V>) BetterSortedMultiMap.super.floorEntry(key);
	}

	@Override
	default ObservableMultiEntry<K, V> ceilingEntry(K key) {
		return (ObservableMultiEntry<K, V>) BetterSortedMultiMap.super.ceilingEntry(key);
	}

	@Override
	default ObservableMultiEntry<K, V> higherEntry(K key) {
		return (ObservableMultiEntry<K, V>) BetterSortedMultiMap.super.higherEntry(key);
	}

	@Override
	default ObservableSortedMultiMap<K, V> reverse() {
		return new ReversedObservableSortedMultiMap<>(this);
	}

	@Override
	default ObservableSortedMultiMap<K, V> subMap(Comparable<? super K> from, Comparable<? super K> to) {
		return new ObservableSubMultiMap<>(this, from, to);
	}

	@Override
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

	@Override
	default ObservableSortedMultiMap<K, V> headMap(K toKey, boolean inclusive) {
		return subMap(null, v -> {
			int comp = keySet().comparator().compare(toKey, v);
			if (!inclusive && comp == 0)
				comp = -1;
			return comp;
		});
	}

	@Override
	default ObservableSortedMultiMap<K, V> tailMap(K fromKey, boolean inclusive) {
		return subMap(v -> {
			int comp = keySet().comparator().compare(fromKey, v);
			if (!inclusive && comp == 0)
				comp = 1;
			return comp;
		}, null);
	}

	@Override
	default ObservableSortedMultiMap<K, V> subMap(K fromKey, K toKey) {
		return subMap(fromKey, true, toKey, true);
	}

	@Override
	default ObservableSortedMultiMap<K, V> headMap(K toKey) {
		return headMap(toKey, true);
	}

	@Override
	default ObservableSortedMultiMap<K, V> tailMap(K fromKey) {
		return tailMap(fromKey, true);
	}

	@Override
	SortedMultiMapFlow<K, V> flow();

	@Override
	default ObservableSortedMap<K, V> single(BiFunction<K, ObservableCollection<V>, ObservableValue<V>> value) {
		return new SortedSingleMap<>(this, value);
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

		@Override
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

	class SortedSingleMap<K, V> extends SingleMap<K, V> implements ObservableSortedMap<K, V> {
		public SortedSingleMap(ObservableSortedMultiMap<K, V> outer, BiFunction<K, ObservableCollection<V>, ObservableValue<V>> valueMap) {
			super(outer, valueMap);
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}

		@Override
		public MapEntryHandle<K, V> search(Comparable<? super K> search, SortedSearchFilter filter) {
			CollectionElement<K> keyEntry = keySet().search(search, filter);
			if (keyEntry == null)
				return null;
			ObservableValue<V> value = getValueMap().apply(keyEntry.get(), getSource().get(keyEntry.get()));
			return new MapEntryHandle<K, V>() {
				@Override
				public ElementId getElementId() {
					return keyEntry.getElementId();
				}

				@Override
				public K getKey() {
					return keyEntry.get();
				}

				@Override
				public V get() {
					return value.get();
				}
			};
		}
	}

	class ReversedObservableSortedMultiMap<K, V> extends BetterSortedMultiMap.ReversedSortedMultiMap<K, V>
	implements ObservableSortedMultiMap<K, V> {
		ReversedObservableSortedMultiMap(BetterSortedMultiMap<K, V> source) {
			super(source);
		}

		@Override
		protected ObservableSortedMultiMap<K, V> getSource() {
			return (ObservableSortedMultiMap<K, V>) super.getSource();
		}

		@Override
		public boolean isLockSupported() {
			return getSource().isLockSupported();
		}

		@Override
		public Comparator<? super K> comparator() {
			return getSource().comparator().reversed();
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
		public TypeToken<ObservableMultiMap.ObservableMultiEntry<K, V>> getEntryType() {
			return getSource().getEntryType();
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}

		@Override
		public ObservableSortedSet<? extends ObservableMultiEntry<K, V>> entrySet() {
			return (ObservableSortedSet<? extends ObservableMultiEntry<K, V>>) super.entrySet();
		}

		@Override
		public ObservableMultiEntry<K, V> getEntry(ElementId keyId) {
			return (ObservableMultiEntry<K, V>) super.getEntry(keyId);
		}

		@Override
		public ObservableMultiEntry<K, V> search(Comparable<? super K> search, SortedSearchFilter filter) {
			return (ObservableMultiEntry<K, V>) super.search(search, filter);
		}

		@Override
		public ObservableCollection<V> get(Object key) {
			return (ObservableCollection<V>) super.get(key);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			try (Transaction t = lock(false, null)) {
				return getSource().onChange(evt -> {
					int keySize = keySet().size();
					if (keySize == 0)
						keySize++; // May have just been removed
					int keyIndex = keySize - evt.getKeyIndex() - 1;
					int valueSize = get(evt.getKey()).size();
					if (valueSize == 0)
						valueSize++; // May have just been removed
					int valueIndex = valueSize - evt.getIndex() - 1;
					ObservableMapEvent<K, V> event = new ObservableMapEvent<>(//
						evt.getKeyElement().reverse(), evt.getElementId().reverse(), getSource().getKeyType(), getSource().getValueType(), //
						keyIndex, valueIndex, evt.getType(), evt.getKey(), evt.getOldValue(), evt.getNewValue(), evt);
					try (Transaction mt = ObservableMapEvent.use(event)) {
						action.accept(event);
					}
				});
			}
		}

		@Override
		public SortedMultiMapFlow<K, V> flow() {
			return getSource().flow().reverse();
		}

		@Override
		public ObservableSortedMultiMap<K, V> reverse() {
			return (ObservableSortedMultiMap<K, V>) super.reverse();
		}
	}

	/**
	 * Implements {@link ObservableSortedMap#subMap(Object, boolean, Object, boolean)}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ObservableSubMultiMap<K, V> extends BetterSortedMultiMap.BetterSubMultiMap<K, V> implements ObservableSortedMultiMap<K, V> {
		public ObservableSubMultiMap(ObservableSortedMultiMap<K, V> outer, Comparable<? super K> lower, Comparable<? super K> upper) {
			super(outer, lower, upper);
		}

		@Override
		protected ObservableSortedMultiMap<K, V> getWrapped() {
			return (ObservableSortedMultiMap<K, V>) super.getWrapped();
		}

		@Override
		public boolean isLockSupported() {
			return getWrapped().isLockSupported();
		}

		@Override
		public Comparator<? super K> comparator() {
			return getWrapped().comparator().reversed();
		}

		@Override
		public TypeToken<K> getKeyType() {
			return getWrapped().getKeyType();
		}

		@Override
		public TypeToken<V> getValueType() {
			return getWrapped().getValueType();
		}

		@Override
		public TypeToken<ObservableMultiEntry<K, V>> getEntryType() {
			return getWrapped().getEntryType();
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}

		@Override
		public ObservableSortedSet<? extends ObservableMultiEntry<K, V>> entrySet() {
			Function<ObservableMultiEntry<K, V>, ObservableMultiEntry<K, V>> map = entry -> entry.reverse();
			return ((ObservableSortedSet<ObservableMultiEntry<K, V>>) getWrapped().entrySet()).reverse().flow()
				.mapEquivalent(getWrapped().getEntryType(), map, map, options -> options.cache(false)).collectPassive();
		}

		@Override
		public ObservableCollection<V> get(Object key) {
			return (ObservableCollection<V>) super.get(key);
		}

		@Override
		public ObservableMultiEntry<K, V> search(Comparable<? super K> search, SortedSearchFilter filter) {
			return (ObservableMultiEntry<K, V>) super.search(search, filter);
		}

		@Override
		public ObservableMultiEntry<K, V> getEntry(ElementId keyId) {
			return (ObservableMultiEntry<K, V>) super.getEntry(keyId);
		}

		@Override
		public ObservableMultiEntry<K, V> getEntry(K key) {
			return (ObservableMultiEntry<K, V>) super.getEntry(key);
		}

		@Override
		public SortedMultiMapFlow<K, V> flow() {
			return getWrapped().flow().withSortedKeys(keys -> keys.filter(k -> {
				if (getLowerBound() != null && getLowerBound().compareTo(k) > 0)
					return StdMsg.ILLEGAL_ELEMENT;
				if (getUpperBound() != null && getUpperBound().compareTo(k) < 0)
					return StdMsg.ILLEGAL_ELEMENT;
				return null;
			}));
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			return getWrapped().onChange(evt -> {
				if (!keySet().belongs(evt.getKey()))
					return;
				int keyIndex = keySet().getElementsBefore(evt.getKeyElement());
				int valueIndex = get(evt.getKey()).getElementsBefore(evt.getElementId());
				ObservableMapEvent<K, V> mapEvent = new ObservableMapEvent<>(evt.getKeyElement(), evt.getElementId(),
					getWrapped().getKeyType(),
					getWrapped().getValueType(), keyIndex, valueIndex, evt.getType(), evt.getKey(), evt.getOldValue(), evt.getNewValue(),
					evt);
				try (Transaction mt = ObservableMapEvent.use(mapEvent)) {
					action.accept(mapEvent);
				}
			});
		}

		@Override
		public String toString() {
			return ObservableMultiMap.toString(this);
		}
	}
}
