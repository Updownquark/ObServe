package org.observe.assoc.impl;

import java.util.Collection;
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
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.observe.collect.ObservableCollectionPassiveManagers.PassiveCollectionManager;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSetImpl;
import org.observe.collect.ObservableSortedSet;
import org.observe.collect.ObservableSortedSetImpl;
import org.observe.util.ObservableCollectionWrapper;
import org.qommons.CausalLock;
import org.qommons.Identifiable;
import org.qommons.Lockable.CoreId;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
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

	private final PassiveCollectionManager<K0, ?, K> theKeyManager;
	private final ObservableSet<K> theKeySet;
	private final PassiveCollectionManager<V0, ?, V> theValueManager;

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
		theValueManager = valueFlow.apply(ObservableSortedSet.<V0> of((v1, v2) -> 0).flow()).managePassive();
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
	protected CausalLock getKeyLocker() {
		return theKeySet;
	}

	@Override
	protected PassiveCollectionManager<V0, ?, V> getValueManager() {
		return theValueManager;
	}

	@Override
	public ObservableSet<K> keySet() {
		return theKeySet;
	}

	/**
	 * @param srcEntry The entry from the source map to wrap
	 * @return An entry for this map, backed by the given source entry
	 */
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
	public ObservableMultiEntry<K, V> watchById(ElementId keyId) {
		ObservableMultiEntry<K0, V0> sourceEntry = theSourceMap.watchById(keyId);
		return new PassivelyDerivedMultiEntry(theKeyManager.map().get().apply(sourceEntry.getKey()), sourceEntry);
	}

	@Override
	public ObservableMultiEntry<K, V> watch(K key) {
		FilterMapResult<K, K0> reversedKey = theKeyManager.reverse(key, false, true);
		if (!reversedKey.isAccepted())
			return new PassivelyDerivedMultiEntry(key, null);
		else
			return new PassivelyDerivedMultiEntry(key, theSourceMap.watch(reversedKey.result));
	}

	@Override
	public MultiEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
		ElementId beforeKey, boolean first, Runnable preAdd, Runnable postAdd) {
		try(Transaction t=lock(true, null)){
			CollectionElement<K> keyEl=keySet().getElement(key, true);
			if(keyEl!=null)
				return getEntryById(keyEl.getElementId());
			FilterMapResult<K, K0> reversedKey = theKeyManager.reverse(key, true, false);
			if(reversedKey.isAccepted())
				return entryFor(theSourceMap.getOrPutEntry(reversedKey.result, k0->{
					Iterable<? extends V> newValues=value.apply(theKeyManager.map().get().apply(k0));
					return new Iterable<V0>() {
						@Override
						public Iterator<V0> iterator() {
							return new Iterator<V0>() {
								private final Iterator<? extends V> source = newValues.iterator();
								boolean hasNext = false;
								V0 next;

								@Override
								public boolean hasNext() {
									while (!hasNext && source.hasNext()) {
										FilterMapResult<V, V0> reversedValue = theValueManager.reverse(source.next(), true, false);
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
				}, afterKey, beforeKey, first ^ theKeyManager.isReversed(), preAdd, postAdd));
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
	public ObservableCollection<V> get(K key) {
		FilterMapResult<K, K0> reversedKey = theKeyManager.reverse(key, false, true);
		if (!reversedKey.isAccepted())
			return theValueFlow.apply(theSourceMap.get((K0) NULL_KEY).flow()).collectPassive();
		return theValueFlow.apply(theSourceMap.get(reversedKey.result).flow()).collectPassive();
	}

	@Override
	public Subscription onChange(Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>> action) {
		return theSourceMap.onChange(sourceEvt -> {
			Function<? super V0, ? extends V> valueMap = theValueManager.map().get();
			V oldValue = null, newValue = null;
			switch (sourceEvt.getType()) {
			case add:
				oldValue = null;
				newValue = valueMap.apply(sourceEvt.getNewValue());
				break;
			case remove:
				oldValue = valueMap.apply(sourceEvt.getOldValue());
				newValue = oldValue;
				break;
			case set:
				newValue = valueMap.apply(sourceEvt.getNewValue());
				if (sourceEvt.getOldValue() == sourceEvt.getNewValue())
					oldValue = newValue;
				else
					oldValue = valueMap.apply(sourceEvt.getOldValue());
				break;
			}
			ObservableMultiMapEvent<K, V> event = new ObservableMultiMapEvent<>(sourceEvt.getKeyElement(), sourceEvt.getElementId(), //
				sourceEvt.getKeyIndex(), sourceEvt.getIndex(), sourceEvt.getType(), //
				theKeyManager.map().get().apply(sourceEvt.getKey()), theKeyManager.map().get().apply(sourceEvt.getOldKey()), //
				oldValue, newValue, sourceEvt, sourceEvt.getMovement());
			try (Transaction evtT = event.use()) {
				action.accept(event);
			}
		});
	}

	class MappedValueCollection extends AbstractIdentifiable implements BetterCollection<V> {
		private final K theKey;
		private final BetterCollection<V0> theSourceValues;

		MappedValueCollection(K key, BetterCollection<V0> sourceValues) {
			theKey = key;
			theSourceValues = sourceValues;
		}

		@Override
		public ThreadConstraint getThreadConstraint() {
			return theSourceValues.getThreadConstraint();
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
		public Collection<Cause> getCurrentCauses() {
			return theSourceValues.getCurrentCauses();
		}

		@Override
		public CoreId getCoreId() {
			return theSourceValues.getCoreId();
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
		public CollectionElement<V> getElement(V value, boolean first) {
			FilterMapResult<V, V0> reversedValue = theValueManager.reverse(value, false, true);
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
		public BetterList<CollectionElement<V>> getElementsBySource(ElementId sourceEl, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(getElement(sourceEl));
			return QommonsUtils.map2(theSourceValues.getElementsBySource(sourceEl, sourceCollection), this::elementFor);
		}

		@Override
		public BetterList<ElementId> getSourceElements(ElementId localElement, BetterCollection<?> sourceCollection) {
			if (sourceCollection == this)
				return BetterList.of(localElement);
			else
				return theSourceValues.getSourceElements(localElement, sourceCollection);
		}

		@Override
		public ElementId getEquivalentElement(ElementId equivalentEl) {
			return theSourceValues.getEquivalentElement(equivalentEl);
		}

		@Override
		public String canAdd(V value, ElementId after, ElementId before) {
			FilterMapResult<V, V0> reversedValue = theValueManager.reverse(value, true, true);
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
			FilterMapResult<V, V0> reversedValue = theValueManager.reverse(value, true, false);
			reversedValue.throwIfRejected();
			boolean reversed = theKeyManager.isReversed();
			return elementFor(theSourceValues.addElement(reversedValue.result, //
				reversed ? ElementId.reverse(before) : after, //
					reversed ? ElementId.reverse(after) : before, //
						first ^ reversed));
		}

		@Override
		public String canMove(ElementId valueEl, ElementId after, ElementId before) {
			boolean reversed = theKeyManager.isReversed();
			return theSourceValues.canMove(//
				reversed ? valueEl.reverse() : valueEl, //
					reversed ? ElementId.reverse(before) : after, //
						reversed ? ElementId.reverse(after) : before);
		}

		@Override
		public CollectionElement<V> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			boolean reversed = theKeyManager.isReversed();
			return elementFor(theSourceValues.move(//
				reversed ? valueEl.reverse() : valueEl, //
					reversed ? ElementId.reverse(before) : after, //
						reversed ? ElementId.reverse(after) : before, first, afterRemove));
		}

		@Override
		public void clear() {
			theSourceValues.clear();
		}

		CollectionElement<V> elementFor(CollectionElement<V0> sourceEl) {
			return new ValueElement(sourceEl);
		}

		MutableCollectionElement<V> mutableElementFor(MutableCollectionElement<V0> sourceEl) {
			return new MutableValueElement(sourceEl);
		}

		class ValueElement implements CollectionElement<V> {
			private final CollectionElement<V0> sourceEl;

			ValueElement(CollectionElement<V0> sourceEl) {
				this.sourceEl = sourceEl;
			}

			CollectionElement<V0> getSourceEl() {
				return sourceEl;
			}

			@Override
			public ElementId getElementId() {
				return sourceEl.getElementId();
			}

			@Override
			public V get() {
				return theValueManager.map().get().apply(sourceEl.get());
			}
		}

		class MutableValueElement extends ValueElement implements MutableCollectionElement<V> {
			MutableValueElement(MutableCollectionElement<V0> sourceEl) {
				super(sourceEl);
			}

			@Override
			MutableCollectionElement<V0> getSourceEl() {
				return (MutableCollectionElement<V0>) super.getSourceEl();
			}

			@Override
			public BetterCollection<V> getCollection() {
				return MappedValueCollection.this;
			}

			@Override
			public String isEnabled() {
				String msg = getValueManager().canReverse();
				if (msg != null)
					return msg;
				return getSourceEl().isEnabled();
			}

			@Override
			public String isAcceptable(V value) {
				FilterMapResult<V, V0> reversed = getValueManager().reverse(value, true, true);
				if (reversed.getRejectReason() != null)
					return reversed.getRejectReason();
				return getSourceEl().isAcceptable(reversed.result);
			}

			@Override
			public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
				FilterMapResult<V, V0> reversed = getValueManager().reverse(value, true, false);
				reversed.throwIfRejected();
				getSourceEl().set(reversed.result);
			}

			@Override
			public String canRemove() {
				return getSourceEl().canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				getSourceEl().remove();
			}
		}
	}

	class PassivelyDerivedMultiEntry extends ObservableCollectionWrapper<V> implements ObservableMultiEntry<K, V> {
		private final K theKey;
		private final ObservableMultiEntry<K0, V0> theSourceEntry;

		PassivelyDerivedMultiEntry(K key, ObservableMultiEntry<K0, V0> sourceEntry) {
			theKey = key;
			theSourceEntry = sourceEntry;
			if (theSourceEntry != null)
				init(theValueFlow.apply(theSourceEntry.flow()).collectPassive());
			else
				init(ObservableCollection.of());
		}

		@Override
		public ElementId getKeyId() {
			return theSourceEntry == null ? null : theSourceEntry.getKeyId();
		}

		@Override
		public K getKey() {
			return theKey;
		}
	}
}
