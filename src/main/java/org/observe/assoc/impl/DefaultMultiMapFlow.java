package org.observe.assoc.impl;

import java.util.function.Function;

import org.observe.Observable;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMap.MultiMapFlow;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.assoc.ObservableSortedMultiMap.SortedMultiMapFlow;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSetImpl;
import org.observe.collect.ObservableSortedSet;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.tree.BetterTreeSet;

/**
 * Default active implementation of {@link MultiMapFlow}
 *
 * @param <S> The type of the source collection whose data the map is to be gathered from
 * @param <K0> The key type of the source map (if any)
 * @param <V0> The value type of the source map (if any)
 * @param <K> The key type for the map
 * @param <V> The value type for the map
 */
public class DefaultMultiMapFlow<S, K0, V0, K, V> implements MultiMapFlow<K, V> {
	private final ObservableMultiMap<K0, V0> theSourceMap;
	private final ObservableCollection<S> theSource;
	private final DistinctDataFlow<S, ?, K> theKeyFlow;
	private final CollectionDataFlow<S, ?, V> theValueFlow;
	private final DistinctDataFlow<K0, ?, K> thePassiveKeyFlow;
	private final Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V>> thePassiveValueFlow;
	private final boolean isPassivePreferred;
	private final AddKeyHolder<K> theAddKey;

	/**
	 * @param sourceMap The source map to support {@link #supportsPassive() passive} map flow
	 * @param source The source collection whose data the map is to be gathered from
	 * @param keyFlow The data flow for the map's key set
	 * @param valueFlow The data flow for all the map's values
	 * @param addKey Stores the key for which the next value is to be added
	 */
	public DefaultMultiMapFlow(ObservableMultiMap<K, V> sourceMap, ObservableCollection<S> source, DistinctDataFlow<S, ?, K> keyFlow,
		CollectionDataFlow<S, ?, V> valueFlow, AddKeyHolder<K> addKey) {
		theSourceMap = (ObservableMultiMap<K0, V0>) sourceMap;
		theSource = source;
		theKeyFlow = keyFlow;
		theValueFlow = valueFlow;
		theAddKey = addKey;
		if (sourceMap != null) {
			thePassiveKeyFlow = (DistinctDataFlow<K0, ?, K>) new ObservableSetImpl.DistinctBaseFlow<>(sourceMap.keySet());
			thePassiveValueFlow = flow -> (CollectionDataFlow<V0, ?, V>) flow;
			isPassivePreferred = true;
		} else {
			thePassiveKeyFlow = null;
			thePassiveValueFlow = null;
			isPassivePreferred = false;
		}
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
	protected DefaultMultiMapFlow(ObservableMultiMap<K0, V0> sourceMap, //
		ObservableCollection<S> source, DistinctDataFlow<S, ?, K> keyFlow, CollectionDataFlow<S, ?, V> valueFlow,
		AddKeyHolder<K> addKey, //
		DistinctDataFlow<K0, ?, K> passiveKeyFlow, Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V>> passiveValueFlow,
		boolean passivePreferred) {
		theSource = source;
		theKeyFlow = keyFlow;
		theValueFlow = valueFlow;
		theAddKey = addKey;
		if (passiveKeyFlow != null && !passiveKeyFlow.supportsPassive()) {
			theSourceMap = sourceMap;
			thePassiveKeyFlow = passiveKeyFlow;
			thePassiveValueFlow = passiveValueFlow;
			isPassivePreferred = passivePreferred;
		} else {
			theSourceMap = null;
			thePassiveKeyFlow = null;
			thePassiveValueFlow = null;
			isPassivePreferred = false;
		}
	}

	/** @return The source collection whose data the map is to be gathered from */
	protected ObservableCollection<S> getSource() {
		return theSource;
	}

	/** @return The data flow for the map's key set */
	protected DistinctDataFlow<S, ?, K> getKeyFlow() {
		return theKeyFlow;
	}

	/** @return The data flow for all the map's values */
	protected CollectionDataFlow<S, ?, V> getValueFlow() {
		return theValueFlow;
	}

	@Override
	public <K2> MultiMapFlow<K2, V> withKeys(Function<DistinctDataFlow<?, ?, K>, DistinctDataFlow<?, ?, K2>> keyMap) {
		DistinctDataFlow<S, ?, K2> newKeyFlow = (DistinctDataFlow<S, ?, K2>) keyMap.apply(theKeyFlow);
		if (newKeyFlow == theKeyFlow)
			return (MultiMapFlow<K2, V>) this;
		DistinctDataFlow<K0, ?, K2> newPassiveKeyFlow = thePassiveKeyFlow == null ? null
			: (DistinctDataFlow<K0, ?, K2>) keyMap.apply(thePassiveKeyFlow);
		AddKeyHolder<K2> newAddKey;
		if (theAddKey == null)
			newAddKey = null;
		else {
			// This is super hacky, but it's the easiest way I can think of to support map addition
			// No worries about thread safety though, since anyone adding a value will obtain a write lock first
			BetterSortedSet<K> keys = BetterTreeSet.<K> buildTreeSet((k1, k2) -> 0).build();
			CollectionElement<K> keyEl = keys.addElement(null, false);
			ObservableSortedSet<K> keySet = ObservableSortedSet.create(keys);
			ObservableSet<K2> derivedKeys = keyMap.apply(keySet.flow()).collect();
			MutableCollectionElement<K2> derivedKeyEl = derivedKeys.mutableElement(derivedKeys.getTerminalElement(true).getElementId());
			newAddKey = theAddKey.map(k2 -> {
				derivedKeyEl.set(k2); // Let it throw an exception if this key or addition in general is unsupported
				return keyEl.get();
			});
		}
		if (newPassiveKeyFlow != null && newPassiveKeyFlow.supportsPassive()) {
			if (newKeyFlow instanceof DistinctSortedDataFlow)
				return new DefaultSortedMultiMapFlow<>((ObservableSortedMultiMap<K0, V0>) theSourceMap, //
					theSource, (DistinctSortedDataFlow<S, ?, K2>) newKeyFlow, theValueFlow, newAddKey,
					(DistinctSortedDataFlow<K0, ?, K2>) newPassiveKeyFlow, thePassiveValueFlow, //
					isPassivePreferred && newPassiveKeyFlow.prefersPassive());
			else
				return new DefaultMultiMapFlow<>(theSourceMap, theSource, newKeyFlow, theValueFlow, newAddKey, newPassiveKeyFlow,
					thePassiveValueFlow, //
					isPassivePreferred && newPassiveKeyFlow.prefersPassive());
		} else {
			if (newKeyFlow instanceof DistinctSortedDataFlow)
				return new DefaultSortedMultiMapFlow<>(null, theSource, (DistinctSortedDataFlow<S, ?, K2>) newKeyFlow, theValueFlow,
					newAddKey);
			else
				return new DefaultMultiMapFlow<>(null, theSource, newKeyFlow, theValueFlow, newAddKey);
		}
	}

	@Override
	public <K2> SortedMultiMapFlow<K2, V> withSortedKeys(Function<DistinctDataFlow<?, ?, K>, DistinctSortedDataFlow<?, ?, K2>> keyMap) {
		return (SortedMultiMapFlow<K2, V>) withKeys(keys -> keyMap.apply(keys));
	}

	@Override
	public <V2> MultiMapFlow<K, V2> withValues(Function<CollectionDataFlow<?, ?, V>, CollectionDataFlow<?, ?, V2>> valueMap) {
		CollectionDataFlow<S, ?, V2> newValueFlow = (CollectionDataFlow<S, ?, V2>) valueMap.apply(theValueFlow);
		if (newValueFlow == theValueFlow)
			return (MultiMapFlow<K, V2>) this;
		boolean passive, passivePreferred;
		if (thePassiveValueFlow == null)
			passive = passivePreferred = false;
		else if (isPassivePreferred && newValueFlow.prefersPassive()) {
			passive = passivePreferred = true;
		} else if (newValueFlow.supportsPassive() && (!isPassivePreferred || theValueFlow.prefersPassive())) {
			passive = true;
			passivePreferred = false;
		} else {
			// This is annoying and kind of hacky, but there's no actual passive value flow instance we can use otherwise
			// It should be safe though, because the flow should never be collected
			CollectionDataFlow<?, ?, V2> hackValueFlow = valueMap
				.apply(theSource.flow().<V> transform(tx -> tx.cache(false).map(s -> (V) s)));
			passive = hackValueFlow.supportsPassive();
			passivePreferred = passive && hackValueFlow.prefersPassive();
		}
		if (passive) {
			Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V2>> newPassiveValueFlow;
			newPassiveValueFlow = flow -> (CollectionDataFlow<V0, ?, V2>) valueMap.apply(//
				thePassiveValueFlow.apply(flow));
			if (theKeyFlow instanceof DistinctSortedDataFlow && theSourceMap instanceof ObservableSortedMultiMap)
				return new DefaultSortedMultiMapFlow<>((ObservableSortedMultiMap<K0, V0>) theSourceMap, theSource,
					(DistinctSortedDataFlow<S, ?, K>) theKeyFlow, newValueFlow, theAddKey, //
					(DistinctSortedDataFlow<K0, ?, K>) thePassiveKeyFlow, newPassiveValueFlow, passivePreferred);
			else
				return new DefaultMultiMapFlow<>(theSourceMap, theSource, theKeyFlow, newValueFlow, theAddKey, //
					thePassiveKeyFlow, newPassiveValueFlow, passivePreferred);
		} else {
			if (theKeyFlow instanceof DistinctSortedDataFlow)
				return new DefaultSortedMultiMapFlow<>(null, theSource, (DistinctSortedDataFlow<S, ?, K>) theKeyFlow, newValueFlow,
					theAddKey);
			else
				return new DefaultMultiMapFlow<>(null, theSource, theKeyFlow, newValueFlow, theAddKey);
		}
	}

	@Override
	public MultiMapFlow<K, V> reverse() {
		DistinctDataFlow<S, ?, K> newKeyFlow = theKeyFlow.reverse();
		CollectionDataFlow<S, ?, V> newValueFlow = theValueFlow.reverse();
		if (thePassiveKeyFlow != null) {
			if (newKeyFlow instanceof DistinctSortedDataFlow)
				return new DefaultSortedMultiMapFlow<>((ObservableSortedMultiMap<K0, V0>) theSourceMap, theSource,
					(DistinctSortedDataFlow<S, ?, K>) newKeyFlow, newValueFlow, theAddKey, //
					(DistinctSortedDataFlow<K0, ?, K>) thePassiveKeyFlow.reverse(), flow -> thePassiveValueFlow.apply(flow).reverse(),
					isPassivePreferred);
			else
				return new DefaultMultiMapFlow<>(theSourceMap, theSource, newKeyFlow, newValueFlow, theAddKey, //
					thePassiveKeyFlow.reverse(), flow -> thePassiveValueFlow.apply(flow).reverse(), isPassivePreferred);
		} else {
			if (newKeyFlow instanceof DistinctSortedDataFlow)
				return new DefaultSortedMultiMapFlow<>(null, theSource, (DistinctSortedDataFlow<S, ?, K>) newKeyFlow, newValueFlow,
					theAddKey);
			else
				return new DefaultMultiMapFlow<>(null, theSource, newKeyFlow, newValueFlow, theAddKey);
		}
	}

	@Override
	public boolean supportsPassive() {
		return thePassiveKeyFlow != null;
	}

	@Override
	public boolean prefersPassive() {
		return isPassivePreferred;
	}

	@Override
	public ObservableMultiMap<K, V> gatherPassive() {
		if (!supportsPassive())
			throw new IllegalStateException("Passive gathering is not supported by this flow");

		if (theKeyFlow instanceof DistinctSortedDataFlow)
			return new DefaultPassiveSortedMultiMap<>((ObservableSortedMultiMap<K0, V0>) theSourceMap, //
				(DistinctSortedDataFlow<K0, ?, K>) thePassiveKeyFlow, thePassiveValueFlow, //
				theSource, (DistinctSortedDataFlow<S, ?, K>) theKeyFlow, theValueFlow, theAddKey);
		else
			return new DefaultPassiveMultiMap<>(theSourceMap, thePassiveKeyFlow, thePassiveValueFlow, //
				theSource, theKeyFlow, theValueFlow, theAddKey);
	}

	@Override
	public ObservableMultiMap<K, V> gatherActive(Observable<?> until) {
		if (until == null)
			until = Observable.empty();

		if (theKeyFlow instanceof DistinctSortedDataFlow)
			return new DefaultActiveSortedMultiMap<>(theSource, (DistinctSortedDataFlow<S, ?, K>) theKeyFlow, theValueFlow, until,
				theAddKey);
		else
			return new DefaultActiveMultiMap<>(theSource, theKeyFlow, theValueFlow, until, theAddKey);
	}
}
