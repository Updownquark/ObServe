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
import org.observe.collect.ObservableSetImpl;

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

	/**
	 * @param sourceMap The source map to support {@link #supportsPassive() passive} map flow
	 * @param source The source collection whose data the map is to be gathered from
	 * @param keyFlow The data flow for the map's key set
	 * @param valueFlow The data flow for all the map's values
	 */
	public DefaultMultiMapFlow(ObservableMultiMap<K, V> sourceMap, ObservableCollection<S> source, DistinctDataFlow<S, ?, K> keyFlow,
		CollectionDataFlow<S, ?, V> valueFlow) {
		theSourceMap = (ObservableMultiMap<K0, V0>) sourceMap;
		theSource = source;
		theKeyFlow = keyFlow;
		theValueFlow = valueFlow;
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

	protected DefaultMultiMapFlow(ObservableMultiMap<K0, V0> sourceMap, //
		ObservableCollection<S> source, DistinctDataFlow<S, ?, K> keyFlow, CollectionDataFlow<S, ?, V> valueFlow, //
		DistinctDataFlow<K0, ?, K> passiveKeyFlow, Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V>> passiveValueFlow,
		boolean passivePreferred) {
		theSource = source;
		theKeyFlow = keyFlow;
		theValueFlow = valueFlow;
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
		if (newPassiveKeyFlow != null && newPassiveKeyFlow.supportsPassive()) {
			if (newKeyFlow instanceof DistinctSortedDataFlow)
				return new DefaultSortedMultiMapFlow<>((ObservableSortedMultiMap<K0, V0>) theSourceMap, //
					theSource, (DistinctSortedDataFlow<S, ?, K2>) newKeyFlow, theValueFlow,
					(DistinctSortedDataFlow<K0, ?, K2>) newPassiveKeyFlow, thePassiveValueFlow, //
					isPassivePreferred && newPassiveKeyFlow.prefersPassive());
			else
				return new DefaultMultiMapFlow<>(theSourceMap, theSource, newKeyFlow, theValueFlow, newPassiveKeyFlow, thePassiveValueFlow, //
					isPassivePreferred && newPassiveKeyFlow.prefersPassive());
		} else {
			if (newKeyFlow instanceof DistinctSortedDataFlow)
				return new DefaultSortedMultiMapFlow<>(null, theSource, (DistinctSortedDataFlow<S, ?, K2>) newKeyFlow, theValueFlow);
			else
				return new DefaultMultiMapFlow<>(null, theSource, newKeyFlow, theValueFlow);
		}
	}

	@Override
	public <K2> SortedMultiMapFlow<K2, V> withSortedKeys(Function<DistinctDataFlow<?, ?, K>, DistinctSortedDataFlow<?, ?, K2>> keyMap) {
		DistinctSortedDataFlow<S, ?, K2> newKeyFlow = (DistinctSortedDataFlow<S, ?, K2>) keyMap.apply(theKeyFlow);
		if (newKeyFlow == theKeyFlow)
			return (SortedMultiMapFlow<K2, V>) this;
		DistinctSortedDataFlow<K0, ?, K2> newPassiveKeyFlow = thePassiveKeyFlow == null ? null
			: (DistinctSortedDataFlow<K0, ?, K2>) keyMap.apply(thePassiveKeyFlow);
		if (newPassiveKeyFlow != null && newPassiveKeyFlow.supportsPassive())
			return new DefaultSortedMultiMapFlow<>((ObservableSortedMultiMap<K0, V0>) theSourceMap, //
				theSource, (DistinctSortedDataFlow<S, ?, K2>) newKeyFlow, theValueFlow, newPassiveKeyFlow, thePassiveValueFlow, //
				isPassivePreferred && newPassiveKeyFlow.prefersPassive());
		else
			return new DefaultSortedMultiMapFlow<>(null, theSource, newKeyFlow, theValueFlow);
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
				.apply(theSource.flow().map(theValueFlow.getTargetType(), s -> (V) s, opts -> opts.cache(false)));
			passive = hackValueFlow.supportsPassive();
			passivePreferred = passive && hackValueFlow.prefersPassive();
		}
		if (passive) {
			Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V2>> newPassiveValueFlow;
			newPassiveValueFlow = flow -> (CollectionDataFlow<V0, ?, V2>) valueMap.apply(//
				thePassiveValueFlow.apply(flow));
			if (theKeyFlow instanceof DistinctSortedDataFlow && theSourceMap instanceof ObservableSortedMultiMap)
				return new DefaultSortedMultiMapFlow<>((ObservableSortedMultiMap<K0, V0>) theSourceMap, theSource,
					(DistinctSortedDataFlow<S, ?, K>) theKeyFlow, newValueFlow, //
					(DistinctSortedDataFlow<K0, ?, K>) thePassiveKeyFlow, newPassiveValueFlow, passivePreferred);
			else
				return new DefaultMultiMapFlow<>(theSourceMap, theSource, theKeyFlow, newValueFlow, //
					thePassiveKeyFlow, newPassiveValueFlow, passivePreferred);
		} else {
			if (theKeyFlow instanceof DistinctSortedDataFlow)
				return new DefaultSortedMultiMapFlow<>(null, theSource, (DistinctSortedDataFlow<S, ?, K>) theKeyFlow, newValueFlow);
			else
				return new DefaultMultiMapFlow<>(null, theSource, theKeyFlow, newValueFlow);
		}
	}

	@Override
	public MultiMapFlow<K, V> reverse() {
		DistinctDataFlow<S, ?, K> newKeyFlow = theKeyFlow.reverse();
		CollectionDataFlow<S, ?, V> newValueFlow = theValueFlow.reverse();
		if (thePassiveKeyFlow != null) {
			if (newKeyFlow instanceof DistinctSortedDataFlow)
				return new DefaultSortedMultiMapFlow<>((ObservableSortedMultiMap<K0, V0>) theSourceMap, theSource,
					(DistinctSortedDataFlow<S, ?, K>) newKeyFlow, newValueFlow, //
					(DistinctSortedDataFlow<K0, ?, K>) thePassiveKeyFlow.reverse(), flow -> thePassiveValueFlow.apply(flow).reverse(),
					isPassivePreferred);
			else
				return new DefaultMultiMapFlow<>(theSourceMap, theSource, newKeyFlow, newValueFlow, //
					thePassiveKeyFlow.reverse(), flow -> thePassiveValueFlow.apply(flow).reverse(), isPassivePreferred);
		} else {
			if (newKeyFlow instanceof DistinctSortedDataFlow)
				return new DefaultSortedMultiMapFlow<>(null, theSource, (DistinctSortedDataFlow<S, ?, K>) newKeyFlow, newValueFlow);
			else
				return new DefaultMultiMapFlow<>(null, theSource, newKeyFlow, newValueFlow);
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
				theSource, (DistinctSortedDataFlow<S, ?, K>) theKeyFlow, theValueFlow);
		else
			return new DefaultPassiveMultiMap<>(theSourceMap, thePassiveKeyFlow, thePassiveValueFlow, //
				theSource, theKeyFlow, theValueFlow);
	}

	@Override
	public ObservableMultiMap<K, V> gatherActive(Observable<?> until) {
		if (until == null)
			until = Observable.empty();

		if (theKeyFlow instanceof DistinctSortedDataFlow)
			return new DefaultActiveSortedMultiMap<>(theSource, (DistinctSortedDataFlow<S, ?, K>) theKeyFlow, theValueFlow, until);
		else
			return new DefaultActiveMultiMap<>(theSource, theKeyFlow, theValueFlow, until);
	}
}
