package org.observe.assoc;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.Equivalence.SortedEquivalence;
import org.observe.Observable.CoreChangeSources;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedSet;
import org.qommons.Identifiable;
import org.qommons.Subscription;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListElement;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableOrderedMapEntry;
import org.qommons.collect.OrderedMapEntry;
import org.qommons.collect.SimpleMapEntry;

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
	ObservableSortedMap<K, V> alias(String alias);

	@Override
	default OrderedMapEntry<K, V> putEntry(K key, V value, boolean first) {
		return BetterSortedMap.super.putEntry(key, value, first);
	}

	@Override
	default OrderedMapEntry<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
		return ObservableMap.super.putEntry(key, value, after, before, first);
	}

	@Override
	default OrderedMapEntry<K, V> getTerminalEntry(boolean first) {
		return BetterSortedMap.super.getTerminalEntry(first);
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
	 * @param sorting The sorting for the map's keys
	 * @return The builder to build the map
	 */
	static <K, V> Builder<K, V, ?> build(Comparator<? super K> sorting) {
		return new Builder<>(sorting, "ObservableMap");
	}

	@Override
	default ObservableSortedMap<K, V> with(K key, V value) {
		ObservableMap.super.with(key, value);
		return this;
	}

	@Override
	default ObservableSortedMap<K, V> withAll(Map<? extends K, ? extends V> values) {
		ObservableMap.super.withAll(values);
		return this;
	}

	@Override
	default ObservableSortedMap<K, V> withAll(Iterable<? extends K> keys, V value) {
		ObservableMap.super.withAll(keys, value);
		return this;
	}

	/**
	 * @param <K> The key-type of the map
	 * @param <V> The value-type of the map
	 * @param sorting The sorting for the map's keys
	 * @return The new observable sorted map
	 */
	static <K, V> ObservableSortedMap<K, V> create(Comparator<? super K> sorting) {
		return create(sorting, null);
	}

	/**
	 * @param <K> The key-type of the map
	 * @param <V> The value-type of the map
	 * @param sorting The sorting for the map's keys
	 * @param build Optional configuration for the new sorted map
	 * @return The new observable sorted map
	 */
	static <K, V> ObservableSortedMap<K, V> create(Comparator<? super K> sorting, Consumer<Builder<K, V, ?>> build) {
		Builder<K, V, ?> builder = build(sorting);
		if (build != null)
			build.accept(builder);
		return builder.buildMap();
	}

	/**
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param sorting The sorting for the map's key set
	 * @return The empty sorted map
	 */
	static <K, V> ObservableSortedMap<K, V> empty(Comparator<? super K> sorting) {
		return new EmptyOSM<>(sorting);
	}

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to wrap
	 * @return An unmodifiable map with the same content as the given map
	 */
	public static <K, V> ObservableSortedMap<K, V> unmodifiable(ObservableSortedMap<K, V> map) {
		return unmodifiable(map, StdMsg.UNSUPPORTED_OPERATION);
	}

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to wrap
	 * @param message The message to report when modification is attempted on the unmodifiable map
	 * @return An {@link ObservableMap} that reflects the given map's contents but does not allow any modifications
	 */
	static <K, V> ObservableSortedMap<K, V> unmodifiable(ObservableSortedMap<K, V> map, String message) {
		return new UnmodifiableSortedObservableMap<>(map, message);
	}

	/**
	 * Builds an unconstrained {@link ObservableMap}
	 *
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param <B> The sub-type of the builder
	 */
	class Builder<K, V, B extends Builder<K, V, ? extends B>> extends ObservableMap.Builder<K, V, B>
	implements ObservableCollectionBuilder.SortedBuilder<K, B> {
		Builder(Comparator<? super K> sorting, String initDescrip) {
			super(initDescrip);
			super.withEquivalence(Equivalence.DEFAULT.sorted(sorting, true));
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
		protected Comparator<? super K> getSorting() {
			return getEquivalence().comparator();
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
			ObservableCollectionBuilder<Entry<K, V>, ?> builder = ObservableCollection.<Entry<K, V>> build()//
				.withBacking((BetterList<Map.Entry<K, V>>) (BetterList<?>) getBacking())//
				.withDescription(getDescription());
			builder.withElementsBySource(getElementsBySource()).withSourceElements(getSourceElements());
			builder.withCollectionLocking(getLocker());
			return new DefaultObservableSortedMap<>(compare, //
				builder.sortBy((entry1, entry2) -> compare.compare(entry1.getKey(), entry2.getKey()))//
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
			theEquivalence = ((Equivalence.SortedEquivalence<K>) getMap().keySet().equivalence()).map(__ -> true,
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
		public ObservableSortedEntrySet<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public ListElement<Map.Entry<K, V>> search(Comparable<? super Map.Entry<K, V>> search, BetterSortedList.SortedSearchFilter filter) {
			MapEntryHandle<K, V> entry = getMap().searchEntries(search, filter);
			return entry == null ? null : getElement(entry.getElementId());
		}

		@Override
		public int indexFor(Comparable<? super Map.Entry<K, V>> search) {
			ListElement<Map.Entry<K, V>> entry = search(search, BetterSortedList.SortedSearchFilter.PreferLess);
			if (entry == null)
				return -1;
			int comp = search.compareTo(entry.get());
			int entryIdx = entry.getElementsBefore();
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
		public ReversedObservableSortedMap<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public boolean isEventing() {
			return theWrapped.isEventing();
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return theWrapped.getChangeSources();
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
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
		public OrderedMapEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId afterKey, ElementId beforeKey,
			boolean first, Runnable preAdd, Runnable postAdd) {
			return OrderedMapEntry.reverse(
				theWrapped.getOrPutEntry(key, value, ElementId.reverse(beforeKey), ElementId.reverse(afterKey), !first, preAdd, postAdd));
		}

		@Override
		public OrderedMapEntry<K, V> searchEntries(Comparable<? super Entry<K, V>> search, BetterSortedList.SortedSearchFilter filter) {
			return OrderedMapEntry.reverse(theWrapped.searchEntries(v -> -search.compareTo(v), filter.opposite()));
		}

		@Override
		public OrderedMapEntry<K, V> putEntry(K key, V value, boolean first) {
			return OrderedMapEntry.reverse(theWrapped.putEntry(key, value, !first));
		}

		@Override
		public OrderedMapEntry<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
			return OrderedMapEntry.reverse(theWrapped.putEntry(key, value, ElementId.reverse(before), ElementId.reverse(after), !first));
		}

		@Override
		public OrderedMapEntry<K, V> getEntry(K key) {
			return OrderedMapEntry.reverse(theWrapped.getEntry(key));
		}

		@Override
		public OrderedMapEntry<K, V> getEntryById(ElementId entryId) {
			return OrderedMapEntry.reverse(theWrapped.getEntryById(entryId.reverse()));
		}

		@Override
		public OrderedMapEntry<K, V> search(Comparable<? super K> search, BetterSortedList.SortedSearchFilter filter) {
			return OrderedMapEntry.reverse(theWrapped.search(v -> -search.compareTo(v), filter.opposite()));
		}

		@Override
		public MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId) {
			return MutableOrderedMapEntry.reverse(theWrapped.mutableEntry(entryId.reverse()));
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
					ObservableMapEvent<K, V> mapEvent = new ObservableMapEvent.Default<>(evt.getElementId().reverse(), index, evt.getType(),
						evt.getOldKey(), evt.getKey(), evt.getOldValue(), evt.getNewValue(), evt, evt.getMovement());
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
		public ObservableSubMap<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public boolean isEventing() {
			return getSource().isEventing();
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return getSource().getChangeSources();
		}

		@Override
		public boolean isLockSupported() {
			return getSource().isLockSupported();
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
				int index = keySet().getElement(evt.getElementId()).getElementsBefore();
				ObservableMapEvent<K, V> mapEvent = new ObservableMapEvent.Default<>(evt.getElementId(), index, evt.getType(),
					evt.getOldKey(), evt.getKey(), evt.getOldValue(), evt.getNewValue(), evt, evt.getMovement());
				try (Transaction t = mapEvent.use()) {
					action.accept(mapEvent);
				}
			});
		}
	}

	/**
	 * A simple, unconstrained {@link ObservableSortedMap} implementation
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class DefaultObservableSortedMap<K, V> extends DefaultObservableMap<K, V> implements ObservableSortedMap<K, V> {
		public DefaultObservableSortedMap(Comparator<? super K> sorting, ObservableSortedCollection<java.util.Map.Entry<K, V>> entries) {
			super(Equivalence.DEFAULT.sorted(sorting, true), entries);
		}

		@Override
		protected ObservableSortedSet<Map.Entry<K, V>> getEntries() {
			return (ObservableSortedSet<Map.Entry<K, V>>) super.getEntries();
		}

		@Override
		public DefaultObservableSortedMap<K, V> alias(String alias) {
			super.alias(alias);
			return this;
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
		protected ObservableSet<Entry<K, V>> createEntrySet() {
			return ObservableSortedMap.super.entrySet();
		}

		@Override
		public OrderedMapEntry<K, V> getEntry(K key) {
			Comparator<? super K> sorting = comparator();
			try {
				return searchEntries(entry -> sorting.compare(key, entry.getKey()), SortedSearchFilter.OnlyMatch);
			} catch (NullPointerException e) {
				/* A very common use case is to make a sorted map with a lambda comparator (e.g. Comparable::compareTo).
				 * In such cases, any query with a null key will result in a NullPointerException.
				 * Ideally, either every comparator would be able to handle null (which I think it is unreasonable to expect of a developer),
				 * or we would be able to detect whether the comparator handles null and deal with it,
				 * but the Comparator API does not allow this.
				 *
				 * In many cases (as here), the intended result of query with a null key into a map that does not handle null keys is clear.
				 * So we can either propagate (or not handle) the exception when the comparator throws it, or we can handle it as it should
				 * be handled with a slight performance hit for allowing the NPE to be thrown.
				 */
				if (key == null) // The comparator must not handle nulls
					return null;
				// else That can't be the problem and the dev needs to know there's some other issue
				throw e;
			}
		}

		@Override
		public OrderedMapEntry<K, V> searchEntries(Comparable<? super Map.Entry<K, V>> search, BetterSortedList.SortedSearchFilter filter) {
			CollectionElement<Entry<K, V>> found = getEntries().search(search, filter);
			return found == null ? null : getEntryById(found.getElementId());
		}

		class SearchEntry implements Map.Entry<K, V> {
			private final K theKey;

			SearchEntry(K key) {
				theKey = key;
			}

			@Override
			public K getKey() {
				return theKey;
			}

			@Override
			public V getValue() {
				return get(theKey);
			}

			@Override
			public V setValue(V value) {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public int hashCode() {
				return Objects.hash(theKey);
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof Map.Entry && Objects.equals(getKey(), ((Map.Entry<?, ?>) obj).getKey());
			}

			@Override
			public String toString() {
				return getKey() + "=?";
			}
		}
	}

	/**
	 * An unmodifiable {@link ObservableSortedMap} wrapper
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class UnmodifiableSortedObservableMap<K, V> extends UnmodifiableObservableMap<K, V> implements ObservableSortedMap<K, V> {
		public UnmodifiableSortedObservableMap(ObservableSortedMap<K, V> wrapped, String message) {
			super(wrapped, message);
		}

		@Override
		protected ObservableSortedMap<K, V> getWrapped() {
			return (ObservableSortedMap<K, V>) super.getWrapped();
		}

		@Override
		public UnmodifiableSortedObservableMap<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}

		@Override
		public OrderedMapEntry<K, V> searchEntries(Comparable<? super Entry<K, V>> search, SortedSearchFilter filter) {
			return getWrapped().searchEntries(search, filter);
		}
	}

	/**
	 * An empty {@link ObservableSortedMap}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class EmptyOSM<K, V> implements ObservableSortedMap<K, V> {
		private final ObservableSortedSet<K> theKeySet;

		public EmptyOSM(Comparator<? super K> sorting) {
			theKeySet = ObservableSortedSet.of(sorting);
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return CoreChangeSources.empty();
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			return Subscription.NONE;
		}

		@Override
		public boolean isEventing() {
			return false;
		}

		@Override
		public OrderedMapEntry<K, V> searchEntries(Comparable<? super Entry<K, V>> search, SortedSearchFilter filter) {
			return null;
		}

		@Override
		public OrderedMapEntry<K, V> getEntry(K key) {
			return null;
		}

		@Override
		public OrderedMapEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public OrderedMapEntry<K, V> getEntryById(ElementId entryId) {
			throw new NoSuchElementException("No such entry: " + entryId);
		}

		@Override
		public MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId) {
			throw new NoSuchElementException("No such entry: " + entryId);
		}

		@Override
		public String canPut(K key, V value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public Object getIdentity() {
			return Identifiable.baseId("EmptySortedMap", this);
		}

		@Override
		public EmptyOSM<K, V> alias(String alias) {
			return this; // Cannot alias this constant
		}

		@Override
		public Set<String> getAliases() {
			return Collections.emptySet();
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Map && ((Map<?, ?>) obj).isEmpty();
		}

		@Override
		public String toString() {
			return "{}";
		}
	}

	/**
	 * Sorted extension of {@link ObservableMap.MappedMap}
	 *
	 * @param <KS> The key-type of the source map
	 * @param <KT> The key-type of this map
	 * @param <VS> The value-type of the source map
	 * @param <VT> The value-type of this map
	 */
	public class MappedSortedMap<KS, KT, VS, VT> extends ObservableMap.MappedMap<KS, KT, VS, VT> implements ObservableSortedMap<KT, VT> {
		/**
		 * @param source The source map to wrap
		 * @param keyMap The function to produce keys for this map from keys in the source map
		 * @param keyReverse The function to produce keys for the source map from keys in this map
		 * @param valueMap The function to produce values for this map from values in the source map
		 * @param valueReverse The function to produce values for the source map from values in this map
		 */
		public MappedSortedMap(ObservableSortedMap<KS, VS> source, Function<? super KS, ? extends KT> keyMap,
			Function<? super KT, ? extends KS> keyReverse, Function<? super VS, ? extends VT> valueMap,
			Function<? super VT, ? extends VS> valueReverse) {
			super(source, keyMap, keyReverse, valueMap, valueReverse);
		}

		@Override
		protected ObservableSortedMap<KS, VS> getSource() {
			return (ObservableSortedMap<KS, VS>) super.getSource();
		}

		@Override
		public MappedSortedMap<KS, KT, VS, VT> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public ObservableSortedSet<KT> keySet() {
			return (ObservableSortedSet<KT>) super.keySet();
		}

		@Override
		public OrderedMapEntry<KT, VT> searchEntries(Comparable<? super Map.Entry<KT, VT>> search, SortedSearchFilter filter) {
			return wrapEntry(getSource().searchEntries(entry -> search.compareTo(//
				new MappedSimpleEntry<>(getKeyMap().apply(entry.getKey()), entry.getValue(), getValueMap())), filter));
		}

		static class MappedSimpleEntry<KS, KT, VS, VT> implements Map.Entry<KT, VT> {
			private final KT theKey;
			private final VS theSourceValue;
			private final Function<? super VS, ? extends VT> theValueMap;
			private VT theValue;

			MappedSimpleEntry(KT key, VS sourceValue, Function<? super VS, ? extends VT> valueMap) {
				theKey = key;
				theSourceValue = sourceValue;
				theValueMap = valueMap;
			}

			@Override
			public KT getKey() {
				return theKey;
			}

			@Override
			public VT getValue() {
				if (theValue == null)
					theValue = theValueMap.apply(theSourceValue);
				return theValue;
			}

			@Override
			public VT setValue(VT value) {
				throw new UnsupportedOperationException();
			}

			@Override
			public int hashCode() {
				return Objects.hashCode(theKey);
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj)
					return true;
				else if (!(obj instanceof Map.Entry))
					return false;
				return Objects.equals(theKey, ((Map.Entry<?, ?>) obj).getKey());
			}

			@Override
			public String toString() {
				return theKey + "=" + getValue();
			}
		}
	}
}
