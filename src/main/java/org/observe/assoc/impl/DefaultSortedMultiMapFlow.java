package org.observe.assoc.impl;

import java.util.function.Function;

import org.observe.Observable;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.assoc.ObservableSortedMultiMap.SortedMultiMapFlow;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;

/**
 * Default active implementation of {@link SortedMultiMapFlow}
 *
 * @param <S> The type of the source collection whose data the map is to be gathered from
 * @param <K0> The key type of the source map (if any)
 * @param <V0> The value type of the source map (if any)
 * @param <K> The key type for the map
 * @param <V> The value type for the map
 */
public class DefaultSortedMultiMapFlow<S, K0, V0, K, V> extends DefaultMultiMapFlow<S, K0, V0, K, V> implements SortedMultiMapFlow<K, V> {
	/**
	 * @param sourceMap The source map to support {@link #supportsPassive() passive} map flow
	 * @param source The source collection whose data the map is to be gathered from
	 * @param keyFlow The data flow for the map's key set
	 * @param valueFlow The data flow for all the map's values
	 * @param addKey Stores the key for which the next value is to be added
	 */
	public DefaultSortedMultiMapFlow(ObservableSortedMultiMap<K, V> sourceMap, ObservableCollection<S> source,
		DistinctSortedDataFlow<S, ?, K> keyFlow, CollectionDataFlow<S, ?, V> valueFlow, AddKeyHolder<K> addKey) {
		super(sourceMap, source, keyFlow, valueFlow, addKey);
	}

	/**
	 * @param sourceMap The source map to support {@link #supportsPassive() passive} map flow
	 * @param source The source collection whose data the map is to be gathered from
	 * @param keyFlow The data flow for the map's key set
	 * @param valueFlow The data flow for all the map's values
	 * @param addKey Stores the key for which the next value is to be added
	 * @param passiveKeyFlow The passive key flow if the flow supports {@link #gatherPassive() passive} gathering
	 * @param passiveValueFlow The passive value flow if the flow supports {@link #gatherPassive() passive} gathering
	 * @param passivePreferred Whether the flow not only supports, but prefers {@link #gatherPassive() passive} gathering
	 */
	protected DefaultSortedMultiMapFlow(ObservableSortedMultiMap<K0, V0> sourceMap, //
		ObservableCollection<S> source, DistinctSortedDataFlow<S, ?, K> keyFlow, CollectionDataFlow<S, ?, V> valueFlow,
		AddKeyHolder<K> addKey, //
		DistinctSortedDataFlow<K0, ?, K> passiveKeyFlow,
		Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V>> passiveValueFlow, boolean passivePreferred) {
		super(sourceMap, source, keyFlow, valueFlow, addKey, passiveKeyFlow, passiveValueFlow, passivePreferred);
	}

	@Override
	protected DistinctSortedDataFlow<S, ?, K> getKeyFlow() {
		return (DistinctSortedDataFlow<S, ?, K>) super.getKeyFlow();
	}

	@Override
	public <V2> SortedMultiMapFlow<K, V2> withValues(Function<CollectionDataFlow<?, ?, V>, CollectionDataFlow<?, ?, V2>> valueMap) {
		return (SortedMultiMapFlow<K, V2>) super.withValues(valueMap);
	}

	@Override
	public SortedMultiMapFlow<K, V> reverse() {
		return (SortedMultiMapFlow<K, V>) super.reverse();
	}

	@Override
	public ObservableSortedMultiMap<K, V> gatherPassive() {
		return (ObservableSortedMultiMap<K, V>) super.gatherPassive();
	}

	@Override
	public ObservableSortedMultiMap<K, V> gatherActive(Observable<?> until) {
		return (ObservableSortedMultiMap<K, V>) super.gatherActive(until);
	}
}
