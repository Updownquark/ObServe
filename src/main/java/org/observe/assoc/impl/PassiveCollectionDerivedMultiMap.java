package org.observe.assoc.impl;

import java.util.function.Function;

import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableSortedSet;

/**
 * Default passive {@link ObservableMultiMap} implementation
 *
 * @param <S> The type of the source collection whose data the map is gathered from
 * @param <K0> The key type of the source map
 * @param <V0> The value type of the source map
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public class PassiveCollectionDerivedMultiMap<S, K0, V0, K, V> extends AbstractPassiveMultiMap<K0, V0, K, V> {
	private final ObservableCollection<S> theSourceCollection;
	private final DistinctDataFlow<S, ?, K> theActiveKeyFlow;
	private final CollectionDataFlow<S, ?, V> theActiveValueFlow;

	/**
	 * @param sourceMap The source map
	 * @param keyFlow The passive key flow
	 * @param valueFlow The function to produce passive value flows from source value flows
	 * @param addKey Stores the key for which the next value is to be added
	 * @param sourceCollection The source collection whose data the source map is gathered from
	 * @param activeKeyFlow The active key flow (unused for this map, but may be used for active maps gathered from this one's
	 *        {@link #flow()})
	 * @param activeValueFlow The active value flow (unused for this map, but may be used for active maps gathered from this one's
	 *        {@link #flow()})
	 */
	public PassiveCollectionDerivedMultiMap(ObservableMultiMap<K0, V0> sourceMap, DistinctDataFlow<K0, ?, K> keyFlow,
		Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V>> valueFlow, AddKeyHolder<K> addKey,
		ObservableCollection<S> sourceCollection, DistinctDataFlow<S, ?, K> activeKeyFlow, CollectionDataFlow<S, ?, V> activeValueFlow) {
		super(sourceMap, keyFlow, valueFlow, addKey);
		theSourceCollection = sourceCollection;
		theActiveKeyFlow = activeKeyFlow;
		theActiveValueFlow = activeValueFlow;
	}

	/** @return The source collection whose data the source map is gathered from */
	protected ObservableCollection<S> getSourceCollection() {
		return theSourceCollection;
	}

	/** @return The active key flow (may or may not be used for this map's keys) */
	protected DistinctDataFlow<S, ?, K> getActiveKeyFlow() {
		return theActiveKeyFlow;
	}

	/** @return The active value flow (may or may not be used for this map's values) */
	protected CollectionDataFlow<S, ?, V> getActiveValueFlow() {
		return theActiveValueFlow;
	}

	@Override
	public MultiMapFlow<K, V> flow() {
		return new DefaultMultiMapFlow<S, K, V, K, V>(this, theSourceCollection, theActiveKeyFlow, theActiveValueFlow, getAddKey()) {
			@Override
			public ObservableMultiMap<K, V> gatherPassive() {
				return PassiveCollectionDerivedMultiMap.this;
			}
		};
	}

	/**
	 * Default sorted passive {@link ObservableMultiMap} implementation
	 *
	 * @param <S> The type of the source collection whose data the map is gathered from
	 * @param <K0> The key type of the source map
	 * @param <V0> The value type of the source map
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	public static class Sorted<S, K0, V0, K, V> extends PassiveCollectionDerivedMultiMap<S, K0, V0, K, V>
	implements ObservableSortedMultiMap<K, V> {
		/**
		 * @param sourceMap The source map
		 * @param keyFlow The passive key flow
		 * @param valueFlow The function to produce passive value flows from source value flows
		 * @param addKey Stores the key for which the next value is to be added
		 * @param sourceCollection The source collection whose data the source map is gathered from
		 * @param activeKeyFlow The active key flow (unused for this map, but may be used for active maps gathered from this one's
		 *        {@link #flow()})
		 * @param activeValueFlow The active value flow (unused for this map, but may be used for active maps gathered from this one's
		 *        {@link #flow()})
		 */
		public Sorted(ObservableSortedMultiMap<K0, V0> sourceMap, DistinctSortedDataFlow<K0, ?, K> keyFlow,
			Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V>> valueFlow, AddKeyHolder<K> addKey,
			ObservableCollection<S> sourceCollection, DistinctSortedDataFlow<S, ?, K> activeKeyFlow,
			CollectionDataFlow<S, ?, V> activeValueFlow) {
			super(sourceMap, keyFlow, valueFlow, addKey, sourceCollection, activeKeyFlow, activeValueFlow);
		}

		@Override
		public ObservableSortedSet<K> keySet() {
			return (ObservableSortedSet<K>) super.keySet();
		}

		@Override
		public ObservableSortedMultiMap<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public SortedMultiMapFlow<K, V> flow() {
			return new DefaultSortedMultiMapFlow<S, K, V, K, V>((ObservableSortedMultiMap<K, V>) this, getSourceCollection(),
				(DistinctSortedDataFlow<S, ?, K>) getActiveKeyFlow(), getActiveValueFlow(), getAddKey()) {
				@Override
				public ObservableSortedMultiMap<K, V> gatherPassive() {
					return Sorted.this;
				}
			};
		}
	}
}
