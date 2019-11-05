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
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableSortedSet;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedMultiMap;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.MultiEntryValueHandle;
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
	default ObservableSortedSet<? extends MultiEntryHandle<K, V>> entrySet() {
		return (ObservableSortedSet<? extends MultiEntryHandle<K, V>>) ObservableMultiMap.super.entrySet();
	}

	@Override
	default MultiEntryHandle<K, V> search(Comparable<? super K> search, BetterSortedList.SortedSearchFilter filter) {
		CollectionElement<K> keyEl = keySet().search(search, filter);
		return keyEl == null ? null : getEntryById(keyEl.getElementId());
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
	default ObservableSortedMap<K, V> singleMap(boolean firstValue) {
		return new SortedSingleMap<>(this, firstValue);
	}

	static <K, V> SortedMultiMapFlow<K, V> create(TypeToken<K> keyType, TypeToken<V> valueType, Comparator<? super K> keyCompare) {
		return create(keyType, valueType, keyCompare, ObservableCollection.createDefaultBacking());
	}

	static <K, V> SortedMultiMapFlow<K, V> create(TypeToken<K> keyType, TypeToken<V> valueType, Comparator<? super K> keyCompare,
		BetterList<Map.Entry<K, V>> entryCollection) {
		return (SortedMultiMapFlow<K, V>) ObservableMultiMap.create(keyType, valueType,
			Equivalence.of(TypeTokens.getRawType(keyType), keyCompare, true));
	}

	interface SortedMultiMapFlow<K, V> extends MultiMapFlow<K, V> {
		<K2> SortedMultiMapFlow<K2, V> withSortedKeys(Function<DistinctSortedDataFlow<?, ?, K>, DistinctSortedDataFlow<?, ?, K2>> keyMap);

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

	/**
	 * Implements {@link ObservableSortedMultiMap#singleMap(boolean)}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class SortedSingleMap<K, V> extends ObservableSingleMap<K, V> implements ObservableSortedMap<K, V> {
		public SortedSingleMap(ObservableSortedMultiMap<K, V> outer, boolean firstValue) {
			super(outer, firstValue);
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}

		@Override
		public MapEntryHandle<K, V> searchEntries(Comparable<? super Entry<K, V>> search, BetterSortedList.SortedSearchFilter filter) {
			CollectionElement<Map.Entry<K, V>> keyEntry = entrySet().search(search, filter);
			if (keyEntry == null)
				return null;
			return new MapEntryHandle<K, V>() {
				@Override
				public ElementId getElementId() {
					return keyEntry.getElementId();
				}

				@Override
				public K getKey() {
					return keyEntry.get().getKey();
				}

				@Override
				public V get() {
					return keyEntry.get().getValue();
				}
			};
		}
	}

	/**
	 * Implements {@link ObservableSortedMultiMap#reverse()}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
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
		public TypeToken<MultiEntryHandle<K, V>> getEntryType() {
			return getSource().getEntryType();
		}

		@Override
		public TypeToken<MultiEntryValueHandle<K, V>> getEntryValueType() {
			return getSource().getEntryValueType();
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}

		@Override
		public ObservableSortedSet<? extends MultiEntryHandle<K, V>> entrySet() {
			return (ObservableSortedSet<? extends MultiEntryHandle<K, V>>) super.entrySet();
		}

		@Override
		public ObservableCollection<V> get(Object key) {
			return (ObservableCollection<V>) super.get(key);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>> action) {
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
					ObservableMultiMapEvent<K, V> event = new ObservableMultiMapEvent<>(//
						evt.getKeyElement().reverse(), evt.getElementId().reverse(), getSource().getKeyType(), getSource().getValueType(), //
						keyIndex, valueIndex, evt.getType(), evt.getKey(), evt.getOldValue(), evt.getNewValue(), evt);
					try (Transaction mt = ObservableMultiMapEvent.use(event)) {
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
		public TypeToken<MultiEntryHandle<K, V>> getEntryType() {
			return getWrapped().getEntryType();
		}

		@Override
		public TypeToken<MultiEntryValueHandle<K, V>> getEntryValueType() {
			return getWrapped().getEntryValueType();
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}

		@Override
		public ObservableSortedSet<? extends MultiEntryHandle<K, V>> entrySet() {
			Function<MultiEntryHandle<K, V>, MultiEntryHandle<K, V>> map = entry -> entry.reverse();
			return ((ObservableSortedSet<MultiEntryHandle<K, V>>) getWrapped().entrySet()).reverse().flow()
				.mapEquivalent(getWrapped().getEntryType(), map, map, options -> options.cache(false)).collectPassive();
		}

		@Override
		public ObservableCollection<V> get(Object key) {
			return (ObservableCollection<V>) super.get(key);
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
		public Subscription onChange(Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>> action) {
			return getWrapped().onChange(evt -> {
				if (!keySet().belongs(evt.getKey()))
					return;
				int keyIndex = keySet().getElementsBefore(evt.getKeyElement());
				int valueIndex = get(evt.getKey()).getElementsBefore(evt.getElementId());
				ObservableMultiMapEvent<K, V> mapEvent = new ObservableMultiMapEvent<>(evt.getKeyElement(), evt.getElementId(),
					getWrapped().getKeyType(),
					getWrapped().getValueType(), keyIndex, valueIndex, evt.getType(), evt.getKey(), evt.getOldValue(), evt.getNewValue(),
					evt);
				try (Transaction mt = ObservableMultiMapEvent.use(mapEvent)) {
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
