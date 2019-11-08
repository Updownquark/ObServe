package org.observe.assoc.impl;

import java.util.function.Function;

import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableSortedSet;

/**
 * Default passive {@link ObservableSortedMultiMap} implementation
 *
 * @param <S> The type of the source collection whose data the map is gathered from
 * @param <K0> The key type of the source map
 * @param <V0> The value type of the source map
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public class DefaultPassiveSortedMultiMap<S, K0, V0, K, V> extends DefaultPassiveMultiMap<S, K0, V0, K, V>
implements ObservableSortedMultiMap<K, V> {
	/**
	 * @param sourceMap The source map
	 * @param keyFlow The passive key flow
	 * @param valueFlow The function to produce passive value flows from source value flows
	 * @param sourceCollection The source collection whose data the source map is gathered from
	 * @param activeKeyFlow The active key flow (unused for this map's keys, but may be used for active maps gathered from this one's
	 *        {@link #flow()})
	 * @param activeValueFlow The active value flow (unused for this map's values, but may be used for active maps gathered from this one's
	 *        {@link #flow()})
	 * @param addKey Stores the key for which the next value is to be added
	 */
	public DefaultPassiveSortedMultiMap(ObservableSortedMultiMap<K0, V0> sourceMap, DistinctSortedDataFlow<K0, ?, K> keyFlow,
		Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V>> valueFlow, ObservableCollection<S> sourceCollection,
		DistinctSortedDataFlow<S, ?, K> activeKeyFlow, CollectionDataFlow<S, ?, V> activeValueFlow, AddKeyHolder<K> addKey) {
		super(sourceMap, keyFlow, valueFlow, sourceCollection, activeKeyFlow, activeValueFlow, addKey);
	}

	@Override
	protected ObservableSortedMultiMap<K0, V0> getSourceMap() {
		return (ObservableSortedMultiMap<K0, V0>) super.getSourceMap();
	}

	@Override
	public ObservableSortedSet<K> keySet() {
		return (ObservableSortedSet<K>) super.keySet();
	}

	@Override
	public SortedMultiMapFlow<K, V> flow() {
		return (SortedMultiMapFlow<K, V>) super.flow();
	}
}
