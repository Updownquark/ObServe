package org.observe.assoc.impl;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable.CoreChangeSources;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableMultiMapEvent;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.CollectionElementMove;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableCollectionPassiveManagers.PassiveCollectionManager;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSetImpl;
import org.observe.collect.ObservableSortedSet;
import org.observe.collect.ObservableSortedSetImpl;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.IterableUtils;
import org.qommons.Lockable;
import org.qommons.Lockable.CoreId;
import org.qommons.Subscription;
import org.qommons.ThreadConstrained;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.ElementId;
import org.qommons.collect.MappedBetterCollection;
import org.qommons.collect.OrderedMultiEntry;

/**
 * Default passive {@link ObservableMultiMap} implementation
 *
 * @param <K0> The key type of the source map
 * @param <V0> The value type of the source map
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public abstract class AbstractPassiveMultiMap<K0, V0, K, V> extends Identifiable.AbstractIdentifiable implements ObservableMultiMap<K, V> {
	private static final Object NULL_KEY = new Object();

	private final ObservableMultiMap<K0, V0> theSourceMap;
	private final Function<? super CollectionDataFlow<V0, ?, V0>, ? extends CollectionDataFlow<V0, ?, V>> theValueFlow;

	final PassiveCollectionManager<K0, ?, K> theKeyManager;
	private final ObservableSet<K> theKeySet;
	final PassiveCollectionManager<V0, ?, V> theValueManager;
	private final AddKeyHolder<K> theAddKey;

	/**
	 * @param sourceMap The source map
	 * @param keyFlow The passive key flow
	 * @param valueFlow The function to produce passive value flows from source value flows
	 * @param addKey Stores the key for which the next value is to be added
	 */
	protected AbstractPassiveMultiMap(ObservableMultiMap<K0, V0> sourceMap, DistinctDataFlow<K0, ?, K> keyFlow,
		Function<? super CollectionDataFlow<V0, ?, V0>, ? extends CollectionDataFlow<V0, ?, V>> valueFlow, AddKeyHolder<K> addKey) {
		theSourceMap = sourceMap;
		theValueFlow = valueFlow;
		theAddKey = addKey;

		theKeyManager = keyFlow.managePassive();
		if (keyFlow instanceof DistinctSortedDataFlow)
			theKeySet = new ObservableSortedSetImpl.PassiveDerivedSortedSet<>(//
				(ObservableSortedSet<K0>) theSourceMap.keySet(), theKeyManager, ((DistinctSortedDataFlow<K0, ?, K>) keyFlow).comparator());
		else
			theKeySet = new ObservableSetImpl.PassiveDerivedSet<>(theSourceMap.keySet(), theKeyManager);
		// Using a sorted set here in case the value flow function is expecting sorting and/or distinctness
		theValueManager = valueFlow.apply(ObservableSortedSet.<V0> of((v1, v2) -> 0).flow()).managePassive();
	}

	/** @return The source map this map is derived from */
	protected ObservableMultiMap<K0, V0> getSourceMap() {
		return theSourceMap;
	}

	/** @return The function to produce passive value flows for this map from value flows of the source map */
	protected Function<? super CollectionDataFlow<V0, ?, V0>, ? extends CollectionDataFlow<V0, ?, V>> getPassiveValueFlow() {
		return theValueFlow;
	}

	/** @return Accepts the key for which the next value should be added */
	protected AddKeyHolder<K> getAddKey() {
		return theAddKey;
	}

	@Override
	protected Object createIdentity() {
		return new MultiMapIdentity(theKeyManager.getIdentity(), theValueManager.getIdentity());
	}

	@Override
	public ObservableMultiMap<K, V> alias(String alias) {
		super.alias(alias);
		return this;
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return ThreadConstrained.getThreadConstraint(theKeyManager, theValueManager);
	}

	@Override
	public boolean isEventing() {
		return theKeyManager.isEventing() || theValueManager.isEventing();
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return theSourceMap.getCurrentCauses();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return Lockable.lockAll(//
			Lockable.lockable(theKeyManager, write, cause), Lockable.lockable(theValueManager, write, cause));
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return Lockable.tryLockAll(//
			Lockable.lockable(theKeyManager, write, cause), Lockable.lockable(theValueManager, write, cause));
	}

	@Override
	public CoreId getCoreId() {
		return theKeyManager.getCoreId().and(theValueManager.getCoreId());
	}

	@Override
	public boolean isLockSupported() {
		return theKeyManager.isLockSupported() && theValueManager.isLockSupported();
	}

	@Override
	public CoreChangeSources getChangeSources() {
		return theKeyManager.getChangeSources().union(theValueManager.getChangeSources());
	}

	@Override
	public ObservableSet<K> keySet() {
		return theKeySet;
	}

	/**
	 * @param srcEntry The entry from the source map to wrap
	 * @return An entry for this map, backed by the given source entry
	 */
	protected OrderedMultiEntry<K, V> entryFor(OrderedMultiEntry<K0, V0> srcEntry) {
		class TransformedEntry implements OrderedMultiEntry<K, V> {
			private OrderedMultiEntry<K0, V0> theSourceEntry;
			private BetterCollection<V> theValues;

			TransformedEntry(OrderedMultiEntry<K0, V0> sourceEntry) {
				theSourceEntry = sourceEntry;
			}

			@Override
			public ElementId getElementId() {
				return theSourceEntry.getElementId();
			}

			@Override
			public int getElementsBefore() {
				return theSourceEntry.getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return theSourceEntry.getElementsAfter();
			}

			@Override
			public OrderedMultiEntry<K, V> getAdjacent(boolean next) {
				OrderedMultiEntry<K0, V0> adj = theSourceEntry.getAdjacent(next);
				return adj == null ? null : new TransformedEntry(adj);
			}

			@Override
			public K getKey() {
				return theKeyManager.map().get().apply(theSourceEntry.getKey());
			}

			@Override
			public BetterCollection<V> getValues() {
				if (theValues == null)
					theValues = MappedBetterCollection.map(theSourceEntry.getValues(), //
						v -> theValueManager.map().get().apply(v), //
						v -> theValueManager.reverse(v, true, true).throwIfRejected().result);
				return theValues;
			}
		}
		return new TransformedEntry(srcEntry);
	}

	@Override
	public OrderedMultiEntry<K, V> getEntryById(ElementId keyId) {
		return entryFor(theSourceMap.getEntryById(keyId));
	}

	@Override
	public ObservableMultiEntry<K, V> watchById(ElementId keyId) {
		ObservableMultiEntry<K0, V0> sourceEntry = theSourceMap.watchById(keyId);
		ObservableCollection<V> derivedValues = theValueFlow.apply(sourceEntry.flow()).collectPassive();
		return new PassivelyDerivedObservableMultiEntry<>(theKeyManager.map().get().apply(sourceEntry.getKey()), sourceEntry, derivedValues);
	}

	@Override
	public ObservableMultiEntry<K, V> watch(K key) {
		FilterMapResult<K, K0> reversedKey = theKeyManager.reverse(key, false, true);
		if (!reversedKey.isAccepted())
			return new PassivelyDerivedObservableMultiEntry<>(key, null, ObservableCollection.of());
		else {
			ObservableMultiEntry<K0, V0> sourceEntry = theSourceMap.watch(reversedKey.result);
			ObservableCollection<V> derivedValues = theValueFlow.apply(sourceEntry.flow()).collectPassive();
			return new PassivelyDerivedObservableMultiEntry<>(key, sourceEntry, derivedValues);
		}
	}

	@Override
	public OrderedMultiEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
		ElementId beforeKey, boolean first, Runnable preAdd, Runnable postAdd) {
		FilterMapResult<K, K0> reversedKey = theKeyManager.reverse(key, true, false);
		if (!reversedKey.isAccepted())
			return null;
		return entryFor(theSourceMap.getOrPutEntry(reversedKey.result, //
			__ -> IterableUtils.map(value.apply(key), v -> theValueManager.reverse(v, true, false).throwIfRejected().result), //
			afterKey, beforeKey, first ^ theKeyManager.isReversed(), () -> {
				theAddKey.accept(key);
				if (preAdd != null)
					preAdd.run();
			}, () -> {
				theAddKey.clear();
				if (postAdd != null)
					postAdd.run();
			}));
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
	public ObservableCollection<V> get(K key) {
		FilterMapResult<K, K0> reversedKey = theKeyManager.reverse(key, false, true);
		if (!reversedKey.isAccepted())
			return theValueFlow.apply(theSourceMap.get((K0) NULL_KEY).flow()).collectPassive();
		return theValueFlow.apply(theSourceMap.get(reversedKey.result).flow()).collectPassive();
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>> action) {
		return theSourceMap.onChange(evt -> {
			K oldKey, newKey;
			V oldValue, newValue;
			newKey = theKeyManager.map().get().apply(evt.getKey());
			newValue = theValueManager.map().get().apply(evt.getNewValue());
			switch (evt.getType()) {
			case add:
				oldKey = null;
				oldValue = null;
				break;
			case remove:
				oldKey = newKey;
				oldValue = newValue;
				break;
			case set:
			default:
				if (evt.getOldKey() == evt.getKey())
					oldKey = newKey;
				else
					oldKey = theKeyManager.map().get().apply(evt.getOldKey());
				if (evt.getOldValue() == evt.getNewValue())
					oldValue = newValue;
				else
					oldValue = theValueManager.map().get().apply(evt.getOldValue());
				break;
			}
			action.accept(new PassiveMappedMultiMapEvent<>(evt, oldKey, newKey, oldValue, newValue));
		});
	}

	public static class PassiveMappedMultiMapEvent<KT, VT> implements ObservableMultiMapEvent<KT, VT> {
		private final ObservableMultiMapEvent<?, ?> theSourceEvent;
		private final KT theOldKey;
		private final KT theNewKey;
		private final VT theOldValue;
		private final VT theNewValue;

		public PassiveMappedMultiMapEvent(ObservableMultiMapEvent<?, ?> sourceEvent, KT oldKey, KT newKey, VT oldValue, VT newValue) {
			theSourceEvent = sourceEvent;
			theOldKey = oldKey;
			theNewKey = newKey;
			theOldValue = oldValue;
			theNewValue = newValue;
		}

		@Override
		public CollectionChangeType getType() {
			return theSourceEvent.getType();
		}

		@Override
		public BetterList<Object> getCauses() {
			return theSourceEvent.getCauses();
		}

		@Override
		public Causable getRootCausable() {
			return theSourceEvent.getRootCausable();
		}

		@Override
		public Effect onFinish(CausableKey key) {
			return theSourceEvent.onFinish(key);
		}

		@Override
		public boolean isFinished() {
			return theSourceEvent.isFinished();
		}

		@Override
		public boolean isTerminated() {
			return theSourceEvent.isTerminated();
		}

		@Override
		public Transaction use() {
			return Transaction.NONE;
		}

		@Override
		public boolean isInitial() {
			return theSourceEvent.isInitial();
		}

		@Override
		public ElementId getElementId() {
			return theSourceEvent.getElementId();
		}

		@Override
		public int getIndex() {
			return theSourceEvent.getIndex();
		}

		@Override
		public CollectionElementMove getMovement() {
			return theSourceEvent.getMovement();
		}

		@Override
		public ElementId getKeyElement() {
			return theSourceEvent.getKeyElement();
		}

		@Override
		public int getKeyIndex() {
			return theSourceEvent.getKeyIndex();
		}

		@Override
		public KT getOldKey() {
			return theOldKey;
		}

		@Override
		public KT getKey() {
			return theNewKey;
		}

		@Override
		public VT getOldValue() {
			return theOldValue;
		}

		@Override
		public VT getNewValue() {
			return theNewValue;
		}

		@Override
		public ObservableCollectionEvent<VT> derive(ElementId element, int index) {
			return new ObservableCollectionEvent.ElementChangedCollectionEvent<>(this, element, index);
		}

		@Override
		public <E2> ObservableCollectionEvent<E2> derive(ElementId element, int index, E2 oldValue, E2 newValue) {
			return new ObservableCollectionEvent.ValueChangedCollectionEvent<>(this, element, index, oldValue, newValue);
		}
	}
}
