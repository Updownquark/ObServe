package org.observe.assoc;

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.Equivalence.SortedEquivalence;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.CollectionElement;
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
	default ObservableCollection<V> values() {
		return ObservableMap.super.values();
	}

	@Override
	default ObservableSortedSet<Entry<K, V>> entrySet() {
		return new ObservableSortedEntrySet<>(this);
	}

	@Override
	default ObservableSortedMap<K, V> descendingMap() {
		return new ReversedObservableSortedMap<>(this);
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
	 * Creates a builder to build an unconstrained {@link ObservableSortedMap}
	 *
	 * @param keyType The key type for the map
	 * @param valueType The value type for the map
	 * @param sorting The sorting for the map's keys
	 * @return The builder to build the map
	 */
	static <K, V> Builder<K, V, ?> build(TypeToken<K> keyType, TypeToken<V> valueType, Comparator<? super K> sorting) {
		return new Builder<>(keyType, valueType, sorting, "ObservableMap");
	}

	/**
	 * Builds an unconstrained {@link ObservableMap}
	 *
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param <B> The sub-type of the builder
	 */
	class Builder<K, V, B extends Builder<K, V, B>> extends ObservableMap.Builder<K, V, B>
	implements ObservableCollectionBuilder.SortedBuilder<K, B> {
		Builder(TypeToken<K> keyType, TypeToken<V> valueType, Comparator<? super K> sorting, String initDescrip) {
			super(keyType, valueType, initDescrip);
			super.withEquivalence(Equivalence.DEFAULT.sorted(TypeTokens.getRawType(getType()), sorting, true));
		}

		@Override
		protected Equivalence.SortedEquivalence<? super K> getEquivalence() {
			return (SortedEquivalence<? super K>) super.getEquivalence();
		}

		@Override
		public B withEquivalence(Equivalence<? super K> equivalence) {
			throw new UnsupportedOperationException("Equivalence is determined by the comparator");
		}

		@Override
		public B sortBy(Comparator<? super K> sorting) {
			if (sorting == null)
				throw new IllegalArgumentException("Comparator cannot be null");
			super.sortBy(sorting);
			return (B) this;
		}

		@Override
		public DistinctSortedBuilder<K, ?> distinct() {
			return new ObservableCollectionBuilder.DistinctSortedBuilderImpl<>(this, getEquivalence().comparator());
		}

		@Override
		public ObservableSortedCollection<K> build() {
			return new ObservableCollectionBuilder.SortedBuilderImpl<>(this, getEquivalence().comparator()).build();
		}

		@Override
		public ObservableSortedMap<K, V> buildMap() {
			Comparator<? super K> compare = getSorting();
			return new DefaultObservableSortedMap<>(getType(), getValueType(), compare, //
				DefaultObservableCollection.build(ObservableMap.buildEntryType(getType(), getValueType()))//
				.withBacking((BetterList<Map.Entry<K, V>>) (BetterList<?>) getBacking())//
				.withDescription(getDescription())//
				.withElementSource(getElementSource()).withSourceElements(getSourceElements())//
				.withLocker(this::getLocker)//
				.sortBy((entry1, entry2) -> compare.compare(entry1.getKey(), entry2.getKey()))//
				.build());
		}
	}

	/**
	 * Implements {@link ObservableSortedMap#entrySet()}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ObservableSortedEntrySet<K, V> extends ObservableMap.ObservableEntrySet<K, V> implements ObservableSortedSet<Map.Entry<K, V>> {
		private final Equivalence.SortedEquivalence<Map.Entry<K, V>> theEquivalence;

		public ObservableSortedEntrySet(ObservableSortedMap<K, V> map) {
			super(map);
			Class<Map.Entry<K, V>> type = (Class<Map.Entry<K, V>>) (Class<?>) Map.Entry.class;
			theEquivalence = ((Equivalence.SortedEquivalence<K>) getMap().keySet().equivalence()).map(type, __ -> true,
				k -> new SimpleMapEntry<>(k, null), Map.Entry::getKey);
		}

		@Override
		public Equivalence.SortedEquivalence<? super Entry<K, V>> equivalence() {
			return theEquivalence;
		}

		@Override
		protected ObservableSortedMap<K, V> getMap() {
			return (ObservableSortedMap<K, V>) super.getMap();
		}

		@Override
		public CollectionElement<Map.Entry<K, V>> search(Comparable<? super Map.Entry<K, V>> search,
			BetterSortedList.SortedSearchFilter filter) {
			MapEntryHandle<K, V> entry = getMap().searchEntries(search, filter);
			return entry == null ? null : getElement(entry.getElementId());
		}

		@Override
		public int indexFor(Comparable<? super Map.Entry<K, V>> search) {
			CollectionElement<Map.Entry<K, V>> entry = search(search, BetterSortedList.SortedSearchFilter.PreferLess);
			if (entry == null)
				return -1;
			int comp = search.compareTo(entry.get());
			int entryIdx = getMap().keySet().getElementsBefore(entry.getElementId());
			if (comp == 0)
				return entryIdx;
			else if (comp < 0)
				return -entryIdx - 1;
			else
				return -entryIdx - 2;
		}

		@Override
		public Comparator<? super Map.Entry<K, V>> comparator() {
			return (entry1, entry2) -> getMap().comparator().compare(entry1.getKey(), entry2.getKey());
		}
	}

	/**
	 * Implements {@link ObservableSortedMap#descendingMap()}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ReversedObservableSortedMap<K, V> extends AbstractIdentifiable implements ObservableSortedMap<K, V> {
		private final ObservableSortedMap<K, V> theWrapped;

		public ReversedObservableSortedMap(ObservableSortedMap<K, V> outer) {
			this.theWrapped = outer;
		}

		@Override
		public Object createIdentity() {
			return Identifiable.wrap(theWrapped.getIdentity(), "descendingMap");
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theWrapped.getKeyType();
		}

		@Override
		public TypeToken<V> getValueType() {
			return theWrapped.getValueType();
		}

		@Override
		public TypeToken<Map.Entry<K, V>> getEntryType() {
			return theWrapped.getEntryType();
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return theWrapped.keySet().reverse();
		}

		@Override
		public ObservableSortedSet<Entry<K, V>> entrySet() {
			return theWrapped.entrySet().reverse();
		}

		@Override
		public MapEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId afterKey, ElementId beforeKey,
			boolean first, Runnable added) {
			return MapEntryHandle
				.reverse(theWrapped.getOrPutEntry(key, value, ElementId.reverse(beforeKey), ElementId.reverse(afterKey), !first, added));
		}

		@Override
		public MapEntryHandle<K, V> searchEntries(Comparable<? super Entry<K, V>> search, BetterSortedList.SortedSearchFilter filter) {
			return MapEntryHandle.reverse(theWrapped.searchEntries(v -> -search.compareTo(v), filter.opposite()));
		}

		@Override
		public MapEntryHandle<K, V> putEntry(K key, V value, boolean first) {
			return MapEntryHandle.reverse(theWrapped.putEntry(key, value, !first));
		}

		@Override
		public MapEntryHandle<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
			return MapEntryHandle.reverse(theWrapped.putEntry(key, value, ElementId.reverse(before), ElementId.reverse(after), !first));
		}

		@Override
		public MapEntryHandle<K, V> getEntry(K key) {
			return MapEntryHandle.reverse(theWrapped.getEntry(key));
		}

		@Override
		public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
			return MapEntryHandle.reverse(theWrapped.getEntryById(entryId.reverse()));
		}

		@Override
		public MapEntryHandle<K, V> search(Comparable<? super K> search, BetterSortedList.SortedSearchFilter filter) {
			return MapEntryHandle.reverse(theWrapped.search(v -> -search.compareTo(v), filter.opposite()));
		}

		@Override
		public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
			return MutableMapEntryHandle.reverse(theWrapped.mutableEntry(entryId.reverse()));
		}

		@Override
		public V put(K key, V value) {
			return theWrapped.put(key, value);
		}

		@Override
		public void putAll(Map<? extends K, ? extends V> m) {
			theWrapped.putAll(m);
		}

		@Override
		public V remove(Object key) {
			return theWrapped.remove(key);
		}

		@Override
		public void clear() {
			theWrapped.clear();
		}

		@Override
		public String canPut(K key, V value) {
			return theWrapped.canPut(key, value);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			try (Transaction t = lock(false, null)) {
				int[] size = new int[] { size() };
				return theWrapped.onChange(evt -> {
					if (evt.getType() == CollectionChangeType.add)
						size[0]++;
					int index = size[0] - evt.getIndex() - 1;
					if (evt.getType() == CollectionChangeType.remove)
						size[0]--;
					ObservableMapEvent<K, V> mapEvent = new ObservableMapEvent<>(evt.getElementId().reverse(), theWrapped.getKeyType(),
						theWrapped.getValueType(), index, evt.getType(), evt.isMove(), evt.getKey(), evt.getOldValue(), evt.getNewValue(),
						evt);
					try (Transaction mt = mapEvent.use()) {
						action.accept(mapEvent);
					}
				});
			}
		}

		@Override
		public ObservableSortedMap<K, V> descendingMap() {
			return theWrapped;
		}

		@Override
		public ObservableSortedSet<K> descendingKeySet() {
			return theWrapped.keySet();
		}
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
				int index = keySet().getElementsBefore(evt.getElementId());
				ObservableMapEvent<K, V> mapEvent = new ObservableMapEvent<>(evt.getElementId(), getSource().getKeyType(),
					getSource().getValueType(), index, evt.getType(), evt.isMove(), evt.getKey(), evt.getOldValue(), evt.getNewValue(),
					evt);
				try (Transaction t = mapEvent.use()) {
					action.accept(mapEvent);
				}
			});
		}
	};

	/**
	 * A simple, unconstrained {@link ObservableSortedMap} implementation
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class DefaultObservableSortedMap<K, V> extends DefaultObservableMap<K, V> implements ObservableSortedMap<K, V> {
		public DefaultObservableSortedMap(TypeToken<K> keyType, TypeToken<V> valueType, Comparator<? super K> sorting,
			ObservableCollection<java.util.Map.Entry<K, V>> entries) {
			super(keyType, valueType, Equivalence.DEFAULT.sorted(TypeTokens.getRawType(keyType), sorting, true), entries);
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}

		@Override
		public ObservableSortedSet<Map.Entry<K, V>> entrySet() {
			return (ObservableSortedSet<Map.Entry<K, V>>) super.entrySet();
		}

		@Override
		public MapEntryHandle<K, V> searchEntries(Comparable<? super Map.Entry<K, V>> search, BetterSortedList.SortedSearchFilter filter) {
			CollectionElement<Map.Entry<K, V>> entry = entrySet().search(search, filter);
			return entry == null ? null : getEntryById(entry.getElementId());
		}
	}
}
