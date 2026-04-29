package org.observe.assoc.impl;

import java.util.Collection;

import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.ObservableSortedMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl;
import org.qommons.CausalLock;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.Lockable;
import org.qommons.Lockable.CoreId;
import org.qommons.ThreadConstrained;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterMultiMap;

/**
 * A partial {@link ObservableMultiMap} implementation covering some common method implementations
 *
 * @param <S> The type of the source collection whose data the map is to be gathered from
 * @param <K> The key type for the map
 * @param <V> The value type for the map
 */
public abstract class AbstractDerivedObservableMultiMap<S, K, V> extends AbstractIdentifiable implements ObservableMultiMap<K, V> {
	private final ObservableCollection<S> theSourceCollection;
	private final DistinctDataFlow<S, ?, K> theActiveKeyFlow;
	private final CollectionDataFlow<S, ?, V> theActiveValueFlow;
	private final AddKeyHolder<K> theAddKey;

	/**
	 * @param sourceCollection The source collection whose data the source map is gathered from
	 * @param activeKeyFlow The active key flow (may or may not be used for this map's keys)
	 * @param activeValueFlow The active value flow (may or may not be used for this map's values)
	 * @param addKey Stores the key for which the next value is to be added
	 */
	protected AbstractDerivedObservableMultiMap(ObservableCollection<S> sourceCollection, DistinctDataFlow<S, ?, K> activeKeyFlow,
		CollectionDataFlow<S, ?, V> activeValueFlow, AddKeyHolder<K> addKey) {
		theSourceCollection = sourceCollection;
		theActiveKeyFlow = activeKeyFlow;
		theActiveValueFlow = activeValueFlow;
		theAddKey = addKey;
	}

	/** @return The source collection whose data the source map is gathered from */
	protected ObservableCollection<S> getSourceCollection() {
		return theSourceCollection;
	}

	/** @return The active key flow (may or may not be used for this map's keys) */
	protected DistinctDataFlow<S, ?, K> getActiveKeyFlow() {
		return theActiveKeyFlow;
	}

	/** @return The active value flow (may or may not be used for this map's values) */
	protected CollectionDataFlow<S, ?, V> getActiveValueFlow() {
		return theActiveValueFlow;
	}

	/** @return Accepts the key for which the next value should be added */
	protected AddKeyHolder<K> getAddKey() {
		return theAddKey;
	}

	/** @return The transactable to use to lock the key flow */
	protected abstract CausalLock getKeyLocker();

	/** @return The manager managing this map's values */
	protected abstract ObservableCollectionDataFlowImpl.CollectionOperation<?, ?, V> getValueManager();

	@Override
	protected Object createIdentity() {
		return new MultiMapIdentity(theActiveKeyFlow.getIdentity(), getValueManager().getIdentity());
	}

	@Override
	public AbstractDerivedObservableMultiMap<S, K, V> alias(String alias) {
		super.alias(alias);
		return this;
	}

	@Override
	public ThreadConstraint getThreadConstraint() {
		return ThreadConstrained.getThreadConstraint(getKeyLocker(), getValueManager());
	}

	@Override
	public boolean isEventing() {
		return theSourceCollection.isEventing();
	}

	@Override
	public boolean isLockSupported() {
		return getKeyLocker().isLockSupported() && getValueManager().isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return Lockable.lockAll(//
			Lockable.lockable(getKeyLocker(), write, cause), Lockable.lockable(getValueManager(), write, cause));
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return Lockable.tryLockAll(//
			Lockable.lockable(getKeyLocker(), write, cause), Lockable.lockable(getValueManager(), write, cause));
	}

	@Override
	public Collection<Cause> getCurrentCauses() {
		return getKeyLocker().getCurrentCauses();
	}

	@Override
	public CoreId getCoreId() {
		return Lockable.getCoreId(//
			Lockable.lockable(getKeyLocker(), false, null), Lockable.lockable(getValueManager(), false, null));
	}


	@Override
	public MultiMapFlow<K, V> flow() {
		if (this instanceof ObservableSortedMultiMap) {
			return new DefaultSortedMultiMapFlow<S, K, V, K, V>((ObservableSortedMultiMap<K, V>) this, theSourceCollection,
				(DistinctSortedDataFlow<S, ?, K>) theActiveKeyFlow, theActiveValueFlow, theAddKey) {
				@Override
				public ObservableSortedMultiMap<K, V> gatherPassive() {
					return (ObservableSortedMultiMap<K, V>) AbstractDerivedObservableMultiMap.this;
				}
			};
		} else {
			return new DefaultMultiMapFlow<S, K, V, K, V>(this, theSourceCollection, theActiveKeyFlow, theActiveValueFlow, theAddKey) {
				@Override
				public ObservableMultiMap<K, V> gatherPassive() {
					return AbstractDerivedObservableMultiMap.this;
				}
			};
		}
	}

	@Override
	public int hashCode() {
		return BetterMultiMap.hashCode(this);
	}

	@Override
	public boolean equals(Object obj) {
		return BetterMultiMap.equals(this, obj);
	}

	@Override
	public String toString() {
		return BetterMultiMap.toString(this);
	}
}
