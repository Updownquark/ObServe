package org.observe.assoc.impl;

import org.observe.Observable;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableSortedSet;

public class DefaultActiveSortedMultiMap<S, K, V> extends DefaultActiveMultiMap<S, K, V>
implements ObservableSortedMultiMap<K, V> {

	public DefaultActiveSortedMultiMap(ObservableCollection<S> source, DistinctSortedDataFlow<S, ?, K> keyFlow,
		CollectionDataFlow<S, ?, V> valueFlow, Observable<?> until) {
		super(source, keyFlow, valueFlow, until);
	}

	@Override
	protected DistinctSortedDataFlow<S, ?, K> getKeyFlow() {
		return (DistinctSortedDataFlow<S, ?, K>) super.getKeyFlow();
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
