package org.observe.assoc.impl;

import java.util.function.Function;

import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableSortedSet;

public class DefaultPassiveSortedMultiMap<S, K0, V0, K, V> extends DefaultPassiveMultiMap<S, K0, V0, K, V>
implements ObservableSortedMultiMap<K, V> {
	public DefaultPassiveSortedMultiMap(ObservableSortedMultiMap<K0, V0> sourceMap, DistinctSortedDataFlow<K0, ?, K> keyFlow,
		Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V>> valueFlow, ObservableCollection<S> sourceCollection,
		DistinctSortedDataFlow<S, ?, K> activeKeyFlow, CollectionDataFlow<S, ?, V> activeValueFlow) {
		super(sourceMap, keyFlow, valueFlow, sourceCollection, activeKeyFlow, activeValueFlow);
	}

	@Override
	protected ObservableSortedMultiMap<K0, V0> getSourceMap() {
		return (ObservableSortedMultiMap<K0, V0>) super.getSourceMap();
	}

	@Override
	public ObservableSortedSet<K> keySet() {
		// TODO Auto-generated method stub
	}

	@Override
	public SortedMultiMapFlow<K, V> flow() {
		return (SortedMultiMapFlow<K, V>) super.flow();
	}
}
