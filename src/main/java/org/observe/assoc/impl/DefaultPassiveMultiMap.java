package org.observe.assoc.impl;

import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMapEvent;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableSet;
import org.qommons.Transaction;
import org.qommons.collect.ElementId;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.MultiEntryValueHandle;

import com.google.common.reflect.TypeToken;

public class DefaultPassiveMultiMap<S, K0, V0, K, V> implements ObservableMultiMap<K, V> {
	private final ObservableMultiMap<K0, V0> theSourceMap;
	private final DistinctDataFlow<K0, ?, K> theKeyFlow;
	private final Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V>> theValueFlow;

	private final ObservableCollection<S> theSourceCollection;
	private final DistinctDataFlow<S, ?, K> theActiveKeyFlow;
	private final CollectionDataFlow<S, ?, V> theActiveValueFlow;

	public DefaultPassiveMultiMap(ObservableMultiMap<K0, V0> sourceMap, DistinctDataFlow<K0, ?, K> keyFlow,
		Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V>> valueFlow, ObservableCollection<S> sourceCollection,
		DistinctDataFlow<S, ?, K> activeKeyFlow, CollectionDataFlow<S, ?, V> activeValueFlow) {
		theSourceMap = sourceMap;
		theKeyFlow = keyFlow;
		theValueFlow = valueFlow;
		theSourceCollection = sourceCollection;
		theActiveKeyFlow = activeKeyFlow;
		theActiveValueFlow = activeValueFlow;
	}

	protected ObservableMultiMap<K0, V0> getSourceMap() {
		return theSourceMap;
	}

	protected DistinctDataFlow<K0, ?, K> getKeyFlow() {
		return theKeyFlow;
	}

	protected Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V>> getValueFlow() {
		return theValueFlow;
	}

	@Override
	public MultiEntryHandle<K, V> getEntryById(ElementId keyId) {
		// TODO Auto-generated method stub
	}

	@Override
	public MultiEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
		ElementId beforeKey, boolean first, Runnable added) {
		// TODO Auto-generated method stub
	}

	@Override
	public int valueSize() {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean clear() {
		// TODO Auto-generated method stub
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		// TODO Auto-generated method stub
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		// TODO Auto-generated method stub
	}

	@Override
	public long getStamp() {
		// TODO Auto-generated method stub
	}

	@Override
	public Object getIdentity() {
		// TODO Auto-generated method stub
	}

	@Override
	public TypeToken<K> getKeyType() {
		// TODO Auto-generated method stub
	}

	@Override
	public TypeToken<V> getValueType() {
		// TODO Auto-generated method stub
	}

	@Override
	public TypeToken<MultiEntryHandle<K, V>> getEntryType() {
		// TODO Auto-generated method stub
	}

	@Override
	public TypeToken<MultiEntryValueHandle<K, V>> getEntryValueType() {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean isLockSupported() {
		// TODO Auto-generated method stub
	}

	@Override
	public ObservableSet<K> keySet() {
		// TODO Auto-generated method stub
	}

	@Override
	public ObservableCollection<V> get(Object key) {
		// TODO Auto-generated method stub
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>> action) {
		// TODO Auto-generated method stub
	}

	@Override
	public MultiMapFlow<K, V> flow() {
		if (theKeyFlow instanceof DistinctSortedDataFlow)
			return new DefaultSortedMultiMapFlow<>((ObservableSortedMultiMap<K, V>) this, theSourceCollection,
				(DistinctSortedDataFlow<S, ?, K>) theActiveKeyFlow, theActiveValueFlow);
		else
			return new DefaultMultiMapFlow<>(this, theSourceCollection, theActiveKeyFlow, theActiveValueFlow);
	}
}
