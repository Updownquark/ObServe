package org.observe.assoc.impl;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Subscription;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMapEvent;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionOperation;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSetImpl;
import org.observe.collect.ObservableSortedSet;
import org.observe.collect.ObservableSortedSetImpl;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.QommonsUtils;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * Default passive {@link ObservableMultiMap} implementation
 *
 * @param <S> The type of the source collection whose data the map is gathered from
 * @param <K0> The key type of the source map
 * @param <V0> The value type of the source map
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public class DefaultPassiveMultiMap<S, K0, V0, K, V> extends AbstractDerivedObservableMultiMap<S, K, V> {
	private static final Object NULL_KEY = new Object();

	private final ObservableMultiMap<K0, V0> theSourceMap;
	private final Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V>> theValueFlow;

	private final ObservableCollectionDataFlowImpl.PassiveCollectionManager<K0, ?, K> theKeyManager;
	private final ObservableSet<K> theKeySet;
	private final ObservableCollectionDataFlowImpl.PassiveCollectionManager<V0, ?, V> theValueManager;

	/**
	 * @param sourceMap The source map
	 * @param keyFlow The passive key flow
	 * @param valueFlow The function to produce passive value flows from source value flows
	 * @param sourceCollection The source collection whose data the source map is gathered from
	 * @param activeKeyFlow The active key flow (unused for this map, but may be used for active maps gathered from this one's
	 *        {@link #flow()})
	 * @param activeValueFlow The active value flow (unused for this map, but may be used for active maps gathered from this one's
	 *        {@link #flow()})
	 * @param addKey Stores the key for which the next value is to be added
	 */
	public DefaultPassiveMultiMap(ObservableMultiMap<K0, V0> sourceMap, DistinctDataFlow<K0, ?, K> keyFlow,
		Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V>> valueFlow, ObservableCollection<S> sourceCollection,
		DistinctDataFlow<S, ?, K> activeKeyFlow, CollectionDataFlow<S, ?, V> activeValueFlow, AddKeyHolder<K> addKey) {
		super(sourceCollection, activeKeyFlow, activeValueFlow, addKey);
		theSourceMap = sourceMap;
		theValueFlow = valueFlow;

		theKeyManager = keyFlow.managePassive();
		if (keyFlow instanceof DistinctSortedDataFlow)
			theKeySet = new ObservableSortedSetImpl.PassiveDerivedSortedSet<>(//
				(ObservableSortedSet<K0>) theSourceMap.keySet(), theKeyManager, ((DistinctSortedDataFlow<K0, ?, K>) keyFlow).comparator());
		else
			theKeySet = new ObservableSetImpl.PassiveDerivedSet<>(theSourceMap.keySet(), theKeyManager);
		// Using a sorted set here in case the value flow function is expecting sorting and/or distinctness
		theValueManager = valueFlow.apply(ObservableSortedSet.of(theSourceMap.getValueType(), (v1, v2) -> 0).flow()).managePassive();
	}

	/** @return The source map this map is derived from */
	protected ObservableMultiMap<K0, V0> getSourceMap() {
		return theSourceMap;
	}

	/** @return The function to produce passive value flows for this map from value flows of the source map */
	protected Function<CollectionDataFlow<V0, ?, V0>, CollectionDataFlow<V0, ?, V>> getPassiveValueFlow() {
		return theValueFlow;
	}

	@Override
	protected Transactable getKeyLocker() {
		return theKeySet;
	}

	@Override
	protected CollectionOperation<?, ?, V> getValueManager() {
		return theValueManager;
	}

	@Override
	public ObservableSet<K> keySet() {
		return theKeySet;
	}

	protected MultiEntryHandle<K, V> entryFor(MultiEntryHandle<K0, V0> srcEntry) {
		return srcEntry == null ? null : new MultiEntryHandle<K, V>() {
			@Override
			public ElementId getElementId() {
				return srcEntry.getElementId();
			}

			@Override
			public K getKey() {
				return theKeyManager.map().get().apply(srcEntry.getKey());
			}

			@Override
			public BetterCollection<V> getValues() {
				return new MappedValueCollection(getKey(), srcEntry.getValues());
			}
		};
	}

	@Override
	public MultiEntryHandle<K, V> getEntryById(ElementId keyId) {
		return entryFor(theSourceMap.getEntryById(keyId));
	}

	@Override
	public MultiEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
		ElementId beforeKey, boolean first, Runnable added) {
		try(Transaction t=lock(true, null)){
			CollectionElement<K> keyEl=keySet().getElement(key, true);
			if(keyEl!=null)
				return getEntryById(keyEl.getElementId());
			FilterMapResult<K, K0> reversedKey = theKeyManager.reverse(key, true);
			if(reversedKey.isAccepted())
				return entryFor(theSourceMap.getOrPutEntry(reversedKey.result, k0->{
					Iterable<? extends V> newValues=value.apply(theKeyManager.map().get().apply(k0));
					return new Iterable<V0>(){
						@Override
						public Iterator<V0> iterator() {
							return new Iterator<V0>() {
								private final Iterator<? extends V> source = newValues.iterator();
								boolean hasNext = false;
								V0 next;

								@Override
								public boolean hasNext() {
									while (!hasNext && source.hasNext()) {
										FilterMapResult<V, V0> reversedValue = theValueManager.reverse(source.next(), false);
										if (reversedValue.isAccepted()) {
											next = reversedValue.result;
											hasNext = true;
										}
									}
									return hasNext;
								}

								@Override
								public V0 next() {
									if (!hasNext && !hasNext())
										throw new NoSuchElementException();
									return next;
								}
							};
						}
					};
				}, afterKey, beforeKey, first^theKeyManager.isReversed(), added));
			else if (!reversedKey.isError())
				return null;
			else if(reversedKey.getRejectReason().equals(StdMsg.UNSUPPORTED_OPERATION))
				reversedKey.throwIfError(UnsupportedOperationException::new);
			else
				reversedKey.throwIfError(IllegalArgumentException::new);
			throw new IllegalStateException("Shouldn't get here");
		}
	}

	@Override
	public int valueSize() {
		return theSourceMap.valueSize();
	}

	@Override
	public boolean clear() {
		return theSourceMap.clear();
	}

	@Override
	public long getStamp() {
		return theSourceMap.getStamp();
	}

	@Override
	public ObservableCollection<V> get(Object key) {
		if (!theKeySet.belongs(key))
			return theValueFlow.apply(theSourceMap.get(NULL_KEY).flow()).collectPassive();
		FilterMapResult<K, K0> reversedKey = theKeyManager.reverse((K) key, true);
		if (!reversedKey.isAccepted())
			return theValueFlow.apply(theSourceMap.get(NULL_KEY).flow()).collectPassive();
		return theValueFlow.apply(theSourceMap.get(reversedKey.result).flow()).collectPassive();
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>> action) {
		// TODO Auto-generated method stub
	}

	class MappedValueCollection extends AbstractIdentifiable implements BetterCollection<V> {
		private final K theKey;
		private final BetterCollection<V0> theSourceValues;

		MappedValueCollection(K key, BetterCollection<V0> sourceValues) {
			theKey = key;
			theSourceValues = sourceValues;
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theSourceValues.lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return theSourceValues.tryLock(write, cause);
		}

		@Override
		public long getStamp() {
			return theSourceValues.getStamp();
		}

		@Override
		public int size() {
			return theSourceValues.size();
		}

		@Override
		public Object createIdentity() {
			return Identifiable.wrap(DefaultPassiveMultiMap.this.getIdentity(), "values", theKey);
		}

		@Override
		public boolean isEmpty() {
			return theSourceValues.isEmpty();
		}

		@Override
		public boolean belongs(Object o) {
			if (!(TypeTokens.get().isInstance(theValueManager.getTargetType(), o)))
				return false;
			FilterMapResult<V, V0> reversedValue = theValueManager.reverse((V) o, true);
			return reversedValue.isAccepted();
		}

		@Override
		public CollectionElement<V> getElement(V value, boolean first) {
			FilterMapResult<V, V0> reversedValue = theValueManager.reverse(value, true);
			if (!reversedValue.isAccepted())
				return null;
			return elementFor(theSourceValues.getElement(reversedValue.result, first ^ theValueManager.isReversed()));
		}

		@Override
		public CollectionElement<V> getElement(ElementId id) {
			return elementFor(theSourceValues.getElement(theValueManager.isReversed() ? id.reverse() : id));
		}

		@Override
		public CollectionElement<V> getTerminalElement(boolean first) {
			return elementFor(theSourceValues.getTerminalElement(first ^ theValueManager.isReversed()));
		}

		@Override
		public CollectionElement<V> getAdjacentElement(ElementId elementId, boolean next) {
			boolean reverse = theValueManager.isReversed();
			return elementFor(theSourceValues.getAdjacentElement(reverse ? elementId.reverse() : elementId, next ^ reverse));
		}

		@Override
		public MutableCollectionElement<V> mutableElement(ElementId id) {
			return mutableElementFor(theSourceValues.mutableElement(theKeyManager.isReversed() ? id.reverse() : id));
		}

		@Override
		public BetterList<CollectionElement<V>> getElementsBySource(ElementId sourceEl) {
			return QommonsUtils.map2(theSourceValues.getElementsBySource(sourceEl), this::elementFor);
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(localElement);
			else
				return theSourceValues.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public String canAdd(V value, ElementId after, ElementId before) {
			FilterMapResult<V, V0> reversedValue = theValueManager.reverse(value, true);
			if (!reversedValue.isAccepted())
				return reversedValue.getRejectReason();
			boolean reversed = theKeyManager.isReversed();
			return theSourceValues.canAdd(reversedValue.result, //
				reversed ? ElementId.reverse(before) : after, //
					reversed ? ElementId.reverse(after) : before);
		}

		@Override
		public CollectionElement<V> addElement(V value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			FilterMapResult<V, V0> reversedValue = theValueManager.reverse(value, true);
			reversedValue.throwIfRejected();
			boolean reversed = theKeyManager.isReversed();
			return elementFor(theSourceValues.addElement(reversedValue.result, //
				reversed ? ElementId.reverse(before) : after, //
					reversed ? ElementId.reverse(after) : before, //
						first ^ reversed));
		}

		CollectionElement<V> elementFor(CollectionElement<V0> sourceEl) {}

		MutableCollectionElement<V> mutableElementFor(MutableCollectionElement<V0> sourceEl) {}

		@Override
		public void clear() {
			theSourceValues.clear();
		}
	}
}
