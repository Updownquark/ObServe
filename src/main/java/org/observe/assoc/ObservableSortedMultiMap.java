package org.observe.assoc;

import java.util.Comparator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableSortedSet;
import org.observe.util.TypeTokens;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterMultiMap;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedMultiMap;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.MultiEntryValueHandle;

import com.google.common.reflect.TypeToken;

/**
 * A sorted {@link ObservableMultiMap}
 *
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public interface ObservableSortedMultiMap<K, V> extends ObservableMultiMap<K, V>, BetterSortedMultiMap<K, V> {
	@Override
	ObservableSortedSet<K> keySet();

	@Override
	default ObservableSortedSet<? extends MultiEntryHandle<K, V>> entrySet() {
		return new ObservableSortedMultiMapEntrySet<>(this);
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

	/**
	 * A {@link ObservableMultiMap.MultiMapFlow} that produces an {@link ObservableSortedMultiMap}
	 *
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 */
	interface SortedMultiMapFlow<K, V> extends MultiMapFlow<K, V> {
		default <K2> SortedMultiMapFlow<K2, V> withStillSortedKeys(
			Function<DistinctSortedDataFlow<?, ?, K>, DistinctSortedDataFlow<?, ?, K2>> keyMap) {
			return withSortedKeys(keys -> keyMap.apply((DistinctSortedDataFlow<?, ?, K>) keys));
		}

		@Override
		<V2> SortedMultiMapFlow<K, V2> withValues(Function<CollectionDataFlow<?, ?, V>, CollectionDataFlow<?, ?, V2>> valueMap);

		@Override
		SortedMultiMapFlow<K, V> reverse();

		@Override
		default ObservableSortedMultiMap<K, V> gather() {
			return (ObservableSortedMultiMap<K, V>) MultiMapFlow.super.gather();
		}

		@Override
		ObservableSortedMultiMap<K, V> gatherPassive();

		@Override
		ObservableSortedMultiMap<K, V> gatherActive(Observable<?> until);
	}

	/**
	 * Builds a basic {@link ObservableSortedMultiMap}
	 *
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 */
	class Builder<K, V> extends ObservableMultiMap.Builder<K, V> {
		Builder(ObservableCollectionBuilder<MapEntry<K, V>, ?> backingBuilder, TypeToken<K> keyType, TypeToken<V> valueType,
			Comparator<? super K> sorting, String defaultDescrip) {
			super(backingBuilder, keyType, valueType, defaultDescrip);
			super.withKeyEquivalence(Equivalence.DEFAULT.sorted(TypeTokens.getRawType(keyType), sorting, true));
		}

		@Override
		public Builder<K, V> safe(boolean safe) {
			super.safe(safe);
			return this;
		}

		@Override
		public Builder<K, V> withLocker(CollectionLockingStrategy locking) {
			super.withLocker(locking);
			return this;
		}

		@Override
		public ObservableMultiMap.Builder<K, V> withKeyEquivalence(Equivalence<? super K> keyEquivalence) {
			return new ObservableMultiMap.Builder<>(getBackingBuilder(), getKeyType(), getValueType(), getDescrip())//
				.withKeyEquivalence(keyEquivalence).withValueEquivalence(getValueEquivalence());
		}

		@Override
		public Builder<K, V> withValueEquivalence(Equivalence<? super V> valueEquivalence) {
			super.withValueEquivalence(valueEquivalence);
			return this;
		}

		@Override
		public Builder<K, V> withDescription(String description) {
			super.withDescription(description);
			return this;
		}

		@Override
		public ObservableSortedMultiMap<K, V> build(Observable<?> until) {
			return (ObservableSortedMultiMap<K, V>) super.build(until);
		}
	}

	/**
	 * Implements {@link ObservableSortedMultiMap#entrySet()}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ObservableSortedMultiMapEntrySet<K, V> extends ObservableMultiMapEntrySet<K, V>
	implements ObservableSortedSet<MultiEntryHandle<K, V>> {
		private static class SimpleMultiEntry<K, V> implements MultiEntryHandle<K, V> {
			private final K key;

			SimpleMultiEntry(K key) {
				this.key = key;
			}

			@Override
			public ElementId getElementId() {
				return null;
			}

			@Override
			public K getKey() {
				return key;
			}

			@Override
			public BetterCollection<V> getValues() {
				return BetterCollection.empty();
			}
		}

		private final Equivalence.SortedEquivalence<? super MultiEntryHandle<K, V>> theEquivalence;

		public ObservableSortedMultiMapEntrySet(ObservableSortedMultiMap<K, V> map) {
			super(map);
			Class<MultiEntryHandle<? extends K, ?>> type = (Class<MultiEntryHandle<? extends K, ?>>) (Class<?>) MultiEntryHandle.class;
			theEquivalence = ((Equivalence.SortedEquivalence<K>) getMap().keySet().equivalence()).map(type, __ -> true,
				k -> new SimpleMultiEntry<>(k), MultiEntryHandle::getKey);
		}

		@Override
		protected ObservableSortedMultiMap<K, V> getMap() {
			return (ObservableSortedMultiMap<K, V>) super.getMap();
		}

		@Override
		public Equivalence.SortedEquivalence<? super MultiEntryHandle<K, V>> equivalence() {
			return theEquivalence;
		}

		@Override
		public Comparator<? super MultiEntryHandle<K, V>> comparator() {
			return equivalence().comparator();
		}

		@Override
		public CollectionElement<MultiEntryHandle<K, V>> search(Comparable<? super MultiEntryHandle<K, V>> search,
			SortedSearchFilter filter) {
			TempEntry temp = new TempEntry();
			CollectionElement<K> keyEl = getMap().keySet().search(key -> {
				temp.key = key;
				return search.compareTo(temp);
			}, filter);
			return keyEl == null ? null : entryFor(getMap().getEntryById(keyEl.getElementId()));
		}

		@Override
		public int indexFor(Comparable<? super MultiEntryHandle<K, V>> search) {
			TempEntry temp = new TempEntry();
			return getMap().keySet().indexFor(key -> {
				temp.key = key;
				return search.compareTo(temp);
			});
		}

		@Override
		public MultiEntryHandle<K, V>[] toArray() {
			return ObservableSortedSet.super.toArray();
		}

		class TempEntry implements MultiEntryHandle<K, V> {
			K key;

			@Override
			public K getKey() {
				return null;
			}

			@Override
			public ElementId getElementId() {
				throw new IllegalStateException("This method may not be called from a search");
			}

			@Override
			public BetterCollection<V> getValues() {
				throw new IllegalStateException("This method may not be called from a search");
			}
		}
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
	class ReversedObservableSortedMultiMap<K, V> extends ReversedObservableMultiMap<K, V> implements ObservableSortedMultiMap<K, V> {
		ReversedObservableSortedMultiMap(ObservableSortedMultiMap<K, V> source) {
			super(source);
		}

		@Override
		protected ObservableSortedMultiMap<K, V> getSource() {
			return (ObservableSortedMultiMap<K, V>) super.getSource();
		}

		@Override
		public Comparator<? super K> comparator() {
			return getSource().comparator().reversed();
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
		public ObservableSortedMultiMap<K, V> subMap(Comparable<? super K> from, Comparable<? super K> to) {
			return getSource().subMap(from, to).reverse();
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
						keyIndex, valueIndex, evt.getType(), evt.isMove(), evt.getKey(), evt.getOldValue(), evt.getNewValue(), evt);
					try (Transaction mt = event.use()) {
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
			return getSource();
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
				.transformEquivalent(getWrapped().getEntryType(), tx -> tx.cache(false).map(map).withReverse(map)).collectPassive();
		}

		@Override
		public ObservableMultiEntry<K, V> watchById(ElementId keyId) {
			// TODO This is technically incorrect, because the value of this particular key element could potentially change,
			// leaving this map's key set
			getEntryById(keyId);// Check to make sure the element is in this map
			return getWrapped().watchById(keyId);
		}

		@Override
		public ObservableMultiEntry<K, V> watch(K key) {
			if (super.isInRange(key) != 0)
				return ObservableMultiEntry.empty(key, getValueType());
			return getWrapped().watch(key);
		}

		@Override
		public ObservableCollection<V> get(Object key) {
			return (ObservableCollection<V>) super.get(key);
		}

		@Override
		public ObservableSortedMultiMap<K, V> subMap(Comparable<? super K> from, Comparable<? super K> to) {
			return getWrapped().subMap(//
				BetterSortedList.and(getLowerBound(), from, true), //
				BetterSortedList.and(getUpperBound(), to, true));
		}

		@Override
		public SortedMultiMapFlow<K, V> flow() {
			return getWrapped().flow().withStillSortedKeys(keys -> keys.filter(k -> {
				int comp = isInRange(k);
				if (comp < 0)
					return "Key is too low";
				else if (comp > 0)
					return "Key is too high";
				else
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
					getWrapped().getKeyType(), getWrapped().getValueType(), keyIndex, valueIndex, evt.getType(), evt.isMove(), evt.getKey(),
					evt.getOldValue(), evt.getNewValue(), evt);
				try (Transaction mt = mapEvent.use()) {
					action.accept(mapEvent);
				}
			});
		}

		@Override
		public String toString() {
			return BetterMultiMap.toString(this);
		}
	}
}
