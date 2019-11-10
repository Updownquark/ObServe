package org.observe.assoc.impl;

import org.observe.Observable;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;

/**
 * A default, active implementation of {@link ObservableSortedMultiMap}
 * 
 * @param <S> The type of the source collection whose data the map is gathered from
 * @param <K> The key set of the map
 * @param <V> The value set of the map
 */
public class DefaultActiveSortedMultiMap<S, K, V> extends DefaultActiveMultiMap<S, K, V>
implements ObservableSortedMultiMap<K, V> {
	/**
	 * @param source The source collection whose data the map is to be gathered from
	 * @param keyFlow The data flow for the map's key set
	 * @param valueFlow The data flow for all the map's values
	 * @param until The observable that, when fired, will release all of this map's resources
	 * @param addKey Stores the key for which the next value is to be added
	 */
	public DefaultActiveSortedMultiMap(ObservableCollection<S> source, DistinctSortedDataFlow<S, ?, K> keyFlow,
		CollectionDataFlow<S, ?, V> valueFlow, Observable<?> until, AddKeyHolder<K> addKey) {
		super(source, keyFlow, valueFlow, until, addKey);
	}

	@Override
	protected ObservableSet<K> createKeySet() {}

	@Override
	protected DistinctSortedDataFlow<S, ?, K> getActiveKeyFlow() {
		return (DistinctSortedDataFlow<S, ?, K>) super.getActiveKeyFlow();
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
