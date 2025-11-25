package org.observe.assoc;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.CausableChanging;
import org.observe.Equivalence;
import org.observe.Eventable;
import org.observe.LightWeightObservable;
import org.observe.Observable;
import org.observe.Observable.CoreChangeSources;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.assoc.ObservableSortedMultiMap.SortedMultiMapFlow;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.CollectionSubscription;
import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableCollectionImpl.ReversedObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.SettableElement;
import org.observe.util.ObservableCollectionWrapper;
import org.observe.util.ObservableUtils.SubscriptionCause;
import org.qommons.BiTuple;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMultiMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionBuilder;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListElement;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.MultiEntryValueHandle;
import org.qommons.collect.MultiMap;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableListElement;
import org.qommons.collect.MutableMultiMapHandle;
import org.qommons.collect.MutableOrderedMapEntry;
import org.qommons.collect.OrderedMapEntry;
import org.qommons.collect.OrderedMultiEntry;
import org.qommons.collect.SimpleMapEntry;
import org.qommons.collect.SimpleMultiEntry;
import org.qommons.tree.BetterTreeSet;

/**
 * An observable map structure that allows more than one value to be stored per key
 *
 * @param <K> The type of key used by this map
 * @param <V> The type of values stored in this map
 */
public interface ObservableMultiMap<K, V> extends BetterMultiMap<K, V>, Eventable, CausableChanging {
	/**
	 * Returned By {@link ObservableMultiMap#get(Object)}, a collection that also reports info on the searched key
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	interface ObservableMultiEntry<K, V> extends ObservableCollection<V> {
		K getKey();

		ElementId getKeyId();

		@Override
		default ObservableMultiEntry<K, V> reverse() {
			return new ReversedObservableMultiEntry<>(this);
		}

		static <K, V> ObservableMultiEntry<K, V> empty(K key) {
			return new EmptyMultiEntry<>(key);
		}

		/**
		 * Implements {@link ObservableMultiMap.ObservableMultiEntry#reverse()}
		 *
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 */
		class ReversedObservableMultiEntry<K, V> extends ReversedObservableCollection<V> implements ObservableMultiEntry<K, V> {
			public ReversedObservableMultiEntry(ObservableMultiEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected ObservableMultiEntry<K, V> getWrapped() {
				return (ObservableMultiEntry<K, V>) super.getWrapped();
			}

			@Override
			public ReversedObservableMultiEntry<K, V> alias(String alias) {
				super.alias(alias);
				return this;
			}

			@Override
			public ElementId getKeyId() {
				return ElementId.reverse(getWrapped().getKeyId());
			}

			@Override
			public K getKey() {
				return getWrapped().getKey();
			}

			@Override
			public ObservableMultiEntry<K, V> reverse() {
				return getWrapped();
			}
		}

		class EmptyMultiEntry<K, V> extends ObservableCollectionWrapper<V> implements ObservableMultiEntry<K, V> {
			private final K theKey;

			public EmptyMultiEntry(K key) {
				theKey = key;
				init(ObservableCollection.of());
			}

			@Override
			public boolean isEventing() {
				return false;
			}

			@Override
			public EmptyMultiEntry<K, V> alias(String alias) {
				super.alias(alias);
				return this;
			}

			@Override
			public K getKey() {
				return theKey;
			}

			@Override
			public ElementId getKeyId() {
				return null;
			}
		}
	}

	@Override
	abstract boolean isLockSupported();

	/** @return The keys that have least one value in this map */
	@Override
	ObservableSet<K> keySet();

	/**
	 * @param keyId The key element to watch
	 * @return The entry for the given element in this map
	 */
	ObservableMultiEntry<K, V> watchById(ElementId keyId);

	/**
	 * @param key The key to watch
	 * @return The entry for the given key in this map
	 */
	ObservableMultiEntry<K, V> watch(K key);

	/**
	 * @param key The key to get values for
	 * @return The collection of values stored for the given key in this map. Never null.
	 */
	@Override
	default ObservableCollection<V> get(K key) {
		return watch(key);
	}

	@Override
	OrderedMultiEntry<K, V> getEntryById(ElementId keyId);

	@Override
	default OrderedMultiEntry<K, V> getTerminalEntry(boolean first) {
		return (OrderedMultiEntry<K, V>) BetterMultiMap.super.getTerminalEntry(first);
	}

	@Override
	default OrderedMultiEntry<K, V> getAdjacentEntry(ElementId entryId, boolean next) {
		return getEntryById(entryId).getAdjacent(next);
	}

	@Override
	default OrderedMultiEntry<K, V> getEntry(K key) {
		try (Transaction t = lock(false, null)) {
			CollectionElement<K> keyElement = keySet().getElement(key, true);
			return keyElement == null ? null : getEntryById(keyElement.getElementId());
		}
	}

	@Override
	OrderedMultiEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
		ElementId beforeKey, boolean first, Runnable preAdd, Runnable postAdd);

	/**
	 * @param action The action to perform on changes to this map
	 * @return The collection subscription to terminate listening
	 */
	Subscription onChange(Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>> action);

	@Override
	ObservableMultiMap<K, V> alias(String alias);

	/**
	 * @param action The action to perform on initial map values and changes
	 * @param keyForward Whether to subscribe to the key set in forward or reverse order
	 * @param valueForward Whether to subscribe to the value collections for each key in forward or reverse order
	 * @return The collection subscription to terminate listening
	 */
	default CollectionSubscription subscribe(Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>> action, boolean keyForward,
		boolean valueForward) {
		try (Transaction t = lock(false, null)) {
			Subscription sub = onChange(action);
			SubscriptionCause subCause = new SubscriptionCause(null);
			try (Transaction ct = subCause.use()) {
				BetterSet<? extends MultiEntryHandle<K, V>> entrySet = entrySet();
				int keyIndex = keyForward ? 0 : entrySet.size() - 1;
				CollectionElement<? extends MultiEntryHandle<K, V>> entry = entrySet.getTerminalElement(keyForward);
				while (entry != null) {
					int valueIndex = valueForward ? 0 : entry.get().getValues().size() - 1;
					CollectionElement<V> value = entry.get().getValues().getTerminalElement(valueForward);
					while (value != null) {
						ObservableMultiMapEvent<K, V> mapEvent = new ObservableMultiMapEvent.Default<>(entry.getElementId(),
							value.getElementId(), keyIndex, valueIndex, CollectionChangeType.add, entry.get().getKey(),
							entry.get().getKey(), null, value.get(), subCause);
						try (Transaction mt = mapEvent.use()) {
							action.accept(mapEvent);
						}
						value = value.getAdjacent(valueForward);
						valueIndex += valueForward ? 1 : -1;
					}
					entry = entry.getAdjacent(keyForward);
					keyIndex += keyForward ? 1 : -1;
				}
			}
			return removeAll -> {
				if (!removeAll) {
					sub.unsubscribe();
					return;
				}
				try (Transaction unsubT = lock(false, null)) {
					sub.unsubscribe();
					SubscriptionCause unsubCause = new SubscriptionCause(null);
					try (Transaction ct = unsubCause.use()) {
						BetterSet<? extends MultiEntryHandle<K, V>> entrySet = entrySet();
						int keyIndex = !keyForward ? 0 : entrySet.size() - 1;
						CollectionElement<? extends MultiEntryHandle<K, V>> entry = entrySet.getTerminalElement(!keyForward);
						while (entry != null) {
							int valueIndex = !valueForward ? 0 : entry.get().getValues().size() - 1;
							CollectionElement<V> value = entry.get().getValues().getTerminalElement(!valueForward);
							while (value != null) {
								ObservableMultiMapEvent<K, V> mapEvent = new ObservableMultiMapEvent.Default<>(entry.getElementId(),
									value.getElementId(), keyIndex, valueIndex, CollectionChangeType.remove, //
									entry.get().getKey(), entry.get().getKey(), value.get(), value.get(), subCause);
								try (Transaction mt = mapEvent.use()) {
									action.accept(mapEvent);
								}
								entry = entry.getAdjacent(!valueForward);
								valueIndex += !valueForward ? 1 : -1;
							}
							entry = entry.getAdjacent(!keyForward);
							keyIndex += !keyForward ? 1 : -1;
						}
					}
				}
			};
		}
	}

	/**
	 * @return An observable collection of {@link org.qommons.collect.MultiMap.MultiEntry observable entries} of all the key-value set pairs
	 *         stored in this map
	 */
	@Override
	default ObservableSet<? extends MultiEntryHandle<K, V>> entrySet() {
		return new ObservableMultiMapEntrySet<>(this);
	}

	@Override
	default BetterList<V> values() {
		return new ObservableMultiMapValues<>(this);
	}

	/**
	 * @param until An observable to release this method's resources when fired
	 * @return An unmodifiable collection of all values mapped to all keys in this multi-map
	 */
	default ObservableCollection<V> observeValues(Observable<?> until) {
		ObservableCollectionBuilder<V, ?> builder = ObservableCollection.<V> build().withThreadConstraint(getThreadConstraint());
		ObservableCollection<V> values = builder.build();
		BetterSortedSet<BiTuple<ElementId, ElementId>> elements = BetterTreeSet.<BiTuple<ElementId, ElementId>> buildTreeSet((el1, el2) -> {
			int comp = el1.getValue1().compareTo(el2.getValue1());
			if (comp == 0)
				return el1.getValue2().compareTo(el2.getValue2());
			return comp;
		}).build();
		Subscription sub = subscribe(evt -> {
			if (evt.getElementId() == null)
				return; // Key update only, no effect on values
			try (Transaction t = values.lock(true, evt); //
				Transaction moveT = evt.getMovement() != null ? values.lock(true, evt.getMovement()) : Transaction.NONE) {
				switch (evt.getType()) {
				case add:
					ListElement<BiTuple<ElementId, ElementId>> el = elements
					.addElement(new BiTuple<>(evt.getKeyElement(), evt.getElementId()), false);
					values.add(el.getElementsBefore(), evt.getNewValue());
					break;
				case remove:
					el = elements.getElement(new BiTuple<>(evt.getKeyElement(), evt.getElementId()), true);
					values.remove(el.getElementsBefore());
					elements.mutableElement(el.getElementId()).remove();
					break;
				case set:
					el = elements.getElement(new BiTuple<>(evt.getKeyElement(), evt.getElementId()), true);
					values.set(el.getElementsBefore(), evt.getNewValue());
					break;
				}
			}
		}, true, true);
		if (until != null)
			until.take(1).act(__ -> sub.unsubscribe());
		return values.flow().unmodifiable(false).collect();
	}

	/** @return A collection of plain (non-observable) {@link java.util.Map.Entry entries}, one for each value in this map */
	default ObservableCollection<MultiEntryValueHandle<K, V>> observeSingleEntries() {
		return new ObservableSingleEntryCollection<>(this);
	}

	@Override
	default ObservableMultiMap<K, V> reverse() {
		return new ReversedObservableMultiMap<>(this);
	}

	@Override
	default ObservableMap<K, V> singleMap(boolean firstValue) {
		return new ObservableSingleMap<>(this, firstValue);
	}

	/**
	 * @param <X> The type of the target map to build
	 * @param valueType The value type for the target map to build
	 * @param combination A function to combine all values for a key in this map into a value in the target map
	 * @param until An observable that will release the built map's resources
	 * @return A builder to build a map whose values are a combination of all values in this map for the same key
	 */
	default <X> ObservableMap<K, X> observeSingleMap(
		BiFunction<? super ObservableCollection<V>, ? super Observable<?>, ? extends SettableValue<X>> combination, Observable<?> until) {
		return new ActiveObservableSingleMap<>(this, combination, until);
	}

	/**
	 * @return An observable that fires a value whenever anything in this structure changes. This observable will only fire 1 event per
	 *         transaction.
	 */
	default Observable<Causable> changes() {
		return entrySet().simpleChanges();
	}

	@Override
	default Observable<Causable> simpleChanges() {
		return changes();
	}

	/** @return A multi-map data flow that may be used to produce derived maps whose data is based on this map's */
	MultiMapFlow<K, V> flow();

	/**
	 * Builds a basic {@link ObservableMultiMap}
	 *
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @return A builder to build a new {@link ObservableMultiMap}
	 */
	public static <K, V> Builder<K, V, ?> build() {
		return new Builder<>(null, "ObservableMultiMap");
	}

	/**
	 * Builds a basic {@link ObservableMultiMap}
	 *
	 * @param <K> The key type for the map
	 * @param <V> the value type for the map
	 * @param <B> The sub-type of this builder
	 */
	class Builder<K, V, B extends Builder<K, V, ? extends B>> implements CollectionBuilder<B> {
		/** A super-simple map entry class. The key and the value are both mutable. */
		static class MapEntry<K, V> {
			K key;
			V value;

			MapEntry(V value) {
				this.value = value;
			}

			@Override
			public String toString() {
				return new StringBuilder().append(key).append('=').append(value).toString();
			}
		}

		private final ObservableCollectionBuilder<MapEntry<K, V>, ?> theBackingBuilder;
		private Equivalence<? super K> theKeyEquivalence;
		private Equivalence<? super V> theValueEquivalence;

		Builder(ObservableCollectionBuilder<MapEntry<K, V>, ?> backingBuilder, String defaultDescrip) {
			if (backingBuilder == null)
				backingBuilder = (ObservableCollectionBuilder<MapEntry<K, V>, ?>) // Type hackery for performance reasons
				(ObservableCollectionBuilder<?, ?>) //
				DefaultObservableCollection.build();
			theBackingBuilder = backingBuilder;
			theKeyEquivalence = Equivalence.DEFAULT;
			theValueEquivalence = Equivalence.DEFAULT;
		}

		@Override
		public B withCollectionLocking(Function<Object, CollectionLockingStrategy> locking) {
			theBackingBuilder.withCollectionLocking(locking);
			return (B) this;
		}

		@Override
		public B withThreadConstraint(ThreadConstraint threadConstraint) {
			theBackingBuilder.withThreadConstraint(threadConstraint);
			return (B) this;
		}

		/**
		 * @param sorting The sorting for the key set
		 * @return A sorted builder with the same settings as this builder but that will build an {@link ObservableSortedMultiMap}
		 */
		public ObservableSortedMultiMap.Builder<K, V, ?> sortedBy(Comparator<? super K> sorting) {
			return new ObservableSortedMultiMap.Builder<>(theBackingBuilder, sorting, theBackingBuilder.getDescription());
		}

		/**
		 * @param keyEquivalence The key equivalence for the multi-map
		 * @return This builder, or if this builder is {@link #sortedBy(Comparator) sorted}, a new builder with the same settings as this
		 *         one but the given key equivalence
		 */
		public Builder<K, V, ?> withKeyEquivalence(Equivalence<? super K> keyEquivalence) {
			theKeyEquivalence = keyEquivalence;
			return this;
		}

		/**
		 * @param valueEquivalence The value equivalence for the multi-map
		 * @return This builder
		 */
		public B withValueEquivalence(Equivalence<? super V> valueEquivalence) {
			theValueEquivalence = valueEquivalence;
			return (B) this;
		}

		@Override
		public B withDescription(String description) {
			theBackingBuilder.withDescription(description);
			return (B) this;
		}

		protected Equivalence<? super V> getValueEquivalence() {
			return theValueEquivalence;
		}

		protected void setValueEquivalence(Equivalence<? super V> valueEquivalence) {
			theValueEquivalence = valueEquivalence;
		}

		@Override
		public String getDescription() {
			return theBackingBuilder.getDescription();
		}

		protected ObservableCollectionBuilder<MapEntry<K, V>, ?> getBackingBuilder() {
			return theBackingBuilder;
		}

		/**
		 * @param until The observable that, when it fires, will release all of the gathered multi-map's resources
		 * @return The new multi-map
		 */
		public ObservableMultiMap<K, V> build(Observable<?> until) {
			ObservableCollection<MapEntry<K, V>> backing = theBackingBuilder.withDescription(getDescription() + " backing").build();
			MultiMapFlow<K, MapEntry<K, V>> mapFlow;
			Function<MapEntry<K, V>, K> keyMap = LambdaUtils.printableFn(entry -> entry.key, "key", null);
			BiConsumer<MapEntry<K, V>, K> keySet = LambdaUtils.printableBiConsumer((element, newKey) -> element.key = newKey,
				() -> "key-set", null);
			BiFunction<K, MapEntry<K, V>, MapEntry<K, V>> keyReverse = LambdaUtils.printableBiFn((key, entry) -> {
				entry.key = key;
				return entry;
			}, "key-reverse", null);
			Function<MapEntry<K, V>, V> valueMap = LambdaUtils.printableFn(entry -> entry.value, "value", null);
			BiConsumer<MapEntry<K, V>, V> valueSet = LambdaUtils.printableBiConsumer((element, newValue) -> element.value = newValue,
				() -> "value-set", null);
			Function<V, MapEntry<K, V>> addition = LambdaUtils.printableFn(value -> new MapEntry<>(value), "addition", null);
			if (theKeyEquivalence instanceof Equivalence.SortedEquivalence) {
				mapFlow = backing.flow().groupSortedFlow(entries -> entries.<K> transform(tx -> tx.map(keyMap).modifySource(keySet))//
					.distinctSorted(((Equivalence.SortedEquivalence<K>) theKeyEquivalence).comparator(), true), //
					keyReverse);
			} else {
				mapFlow = backing.flow()
					.groupByFlow(entries -> entries
						.<K> transform(tx -> tx.map(keyMap).modifySource(keySet).withEquivalence(theKeyEquivalence)).distinct(),
						keyReverse);
			}
			if (theValueEquivalence instanceof Equivalence.SortedEquivalence) {
				return mapFlow.withValues(entries -> entries.<V> transform(tx -> tx.map(valueMap).modifySource(valueSet, //
					rvrs -> rvrs.createWith(addition))//
					.withEquivalence(theValueEquivalence))//
					.sorted(((Equivalence.SortedEquivalence<? super V>) theValueEquivalence).comparator()))//
					.gatherActive(until);
			} else {
				return mapFlow.withValues(entries -> entries.<V> transform(tx -> tx.map(valueMap).modifySource(valueSet, //
					rvrs -> rvrs.createWith(addition))//
					.withEquivalence(theValueEquivalence)))//
					.gatherActive(until);
			}
		}
	}

	/**
	 * Implements {@link ObservableMultiMap#entrySet()}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ObservableMultiMapEntrySet<K, V> extends BetterMultiMapEntrySet<K, V> implements ObservableSet<MultiEntryHandle<K, V>> {
		private Equivalence<? super MultiMap.MultiEntry<? extends K, ?>> theEquivalence;

		public ObservableMultiMapEntrySet(ObservableMultiMap<K, V> map) {
			super(map);
		}

		@Override
		protected ObservableMultiMap<K, V> getMap() {
			return (ObservableMultiMap<K, V>) super.getMap();
		}

		@Override
		public ObservableMultiMapEntrySet<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public boolean isEventing() {
			return getMap().isEventing();
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return getMap().getChangeSources();
		}

		@Override
		public Equivalence<? super MultiEntryHandle<K, V>> equivalence() {
			if (theEquivalence == null)
				theEquivalence = getMap().keySet().equivalence().map(null, key -> new SimpleMultiEntry<>(key, false),
					MultiMap.MultiEntry::getKey);
			return theEquivalence;
		}

		@Override
		public boolean isContentControlled() {
			return getMap().keySet().isContentControlled();
		}

		@Override
		public ListElement<MultiEntryHandle<K, V>> getTerminalElement(boolean first) {
			return (ListElement<MultiEntryHandle<K, V>>) super.getTerminalElement(first);
		}

		@Override
		public ListElement<MultiEntryHandle<K, V>> getElement(MultiEntryHandle<K, V> value, boolean first) {
			return (ListElement<MultiEntryHandle<K, V>>) super.getElement(value, first);
		}

		@Override
		public ListElement<MultiEntryHandle<K, V>> getElement(ElementId id) {
			return (ListElement<MultiEntryHandle<K, V>>) super.getElement(id);
		}

		@Override
		public ListElement<MultiEntryHandle<K, V>> getElement(int index) throws IndexOutOfBoundsException {
			return entryFor(getMap().getEntryById(getMap().keySet().getElement(index).getElementId()));
		}

		@Override
		public ListElement<MultiEntryHandle<K, V>> addElement(MultiEntryHandle<K, V> value, ElementId after, ElementId before,
			boolean first) throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<MultiEntryHandle<K, V>>) super.addElement(value, after, before, first);
		}

		@Override
		public ListElement<MultiEntryHandle<K, V>> move(ElementId valueEl, ElementId after, ElementId before, boolean first,
			Runnable afterRemove) throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<MultiEntryHandle<K, V>>) super.move(valueEl, after, before, first, afterRemove);
		}

		@Override
		public ListElement<MultiEntryHandle<K, V>> getOrAdd(MultiEntryHandle<K, V> value, ElementId after, ElementId before, boolean first,
			Runnable preAdd, Runnable postAdd) {
			return (ListElement<MultiEntryHandle<K, V>>) super.getOrAdd(value, after, before, first, preAdd, postAdd);
		}

		@Override
		public void setValue(Collection<ElementId> elements, MultiEntryHandle<K, V> value) {
			if (!elements.isEmpty())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public MutableListElement<MultiEntryHandle<K, V>> mutableElement(ElementId id) {
			return (MutableListElement<MultiEntryHandle<K, V>>) super.mutableElement(id);
		}

		@Override
		protected ListElement<MultiEntryHandle<K, V>> entryFor(MultiEntryHandle<K, V> entry) {
			return new OrderedEntrySetElement((OrderedMultiEntry<K, V>) entry);
		}

		@Override
		protected MutableListElement<MultiEntryHandle<K, V>> mutableEntryFor(MultiEntryHandle<K, V> entry) {
			return new MutableOrderedEntrySetElement((OrderedMultiEntry<K, V>) entry);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends MultiEntryHandle<K, V>>> observer) {
			return getMap().onChange(mapEvt -> {
				MultiEntryHandle<K, V> entry;
				if (mapEvt.getKeyElement().isPresent())
					entry = getMap().getEntryById(mapEvt.getKeyElement());
				else
					entry = new SyntheticEntry(mapEvt.getKeyElement(), mapEvt.getKey());
				CollectionChangeType changeType;
				if (mapEvt.getType() == CollectionChangeType.add && entry.getValues().size() == 1)
					changeType = CollectionChangeType.add;
				else if (mapEvt.getType() == CollectionChangeType.remove && entry.getValues().isEmpty())
					changeType = CollectionChangeType.remove;
				else
					changeType = CollectionChangeType.set;

				ObservableCollectionEvent<MultiEntryHandle<K, V>> collEvt = ObservableCollectionEvent.createCollectionEvent(//
					mapEvt.getKeyElement(), mapEvt.getKeyIndex(), changeType, //
					changeType == CollectionChangeType.add ? null : entry, entry, mapEvt, mapEvt.getMovement());
				try (Transaction evtT = collEvt.use()) {
					observer.accept(collEvt);
				}
			});
		}

		private class SyntheticEntry implements MultiEntryHandle<K, V> {
			private final ElementId theKeyId;
			private final K theKey;

			SyntheticEntry(ElementId keyId, K key) {
				theKeyId = keyId;
				theKey = key;
			}

			@Override
			public K getKey() {
				return theKey;
			}

			@Override
			public ElementId getElementId() {
				return theKeyId;
			}

			@Override
			public MultiEntryHandle<K, V> getAdjacent(boolean next) {
				return null;
			}

			@Override
			public BetterCollection<V> getValues() {
				return BetterList.empty();
			}
		}

		class OrderedEntrySetElement extends EntrySetElement implements ListElement<MultiEntryHandle<K, V>> {
			OrderedEntrySetElement(OrderedMultiEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected OrderedMultiEntry<K, V> getEntry() {
				return (OrderedMultiEntry<K, V>) super.getEntry();
			}

			@Override
			public int getElementsBefore() {
				return getEntry().getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return getEntry().getElementsAfter();
			}

			@Override
			public ListElement<MultiEntryHandle<K, V>> getAdjacent(boolean next) {
				OrderedMultiEntry<K, V> adj = getEntry().getAdjacent(next);
				return adj == null ? null : new OrderedEntrySetElement(adj);
			}
		}

		class MutableOrderedEntrySetElement extends MutableEntrySetElement implements MutableListElement<MultiEntryHandle<K, V>> {
			MutableOrderedEntrySetElement(OrderedMultiEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected OrderedMultiEntry<K, V> getEntry() {
				return (OrderedMultiEntry<K, V>) super.getEntry();
			}

			@Override
			public int getElementsBefore() {
				return getEntry().getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return getEntry().getElementsAfter();
			}

			@Override
			public MutableListElement<MultiEntryHandle<K, V>> getAdjacent(boolean next) {
				OrderedMultiEntry<K, V> adj = getEntry().getAdjacent(next);
				return adj == null ? null : new MutableOrderedEntrySetElement(adj);
			}
		}
	}

	/**
	 * Implements {@link ObservableMultiMap#values()}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ObservableMultiMapValues<K, V> extends BetterMultiMapValueCollection<K, V> implements BetterList<V> {
		// I thought about making this observable, but there's not way to make indexing better than linear time,
		// and that's required for the events
		public ObservableMultiMapValues(ObservableMultiMap<K, V> map) {
			super(map);
		}

		@Override
		protected ObservableMultiMap<K, V> getMap() {
			return (ObservableMultiMap<K, V>) super.getMap();
		}

		@Override
		public boolean isLockSupported() {
			return getMap().isLockSupported();
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public ListElement<V> getElement(V value, boolean first) {
			return (ListElement<V>) super.getElement(value, first);
		}

		@Override
		public ListElement<V> getElement(ElementId id) {
			return (ListElement<V>) super.getElement(id);
		}

		@Override
		public ListElement<V> getTerminalElement(boolean first) {
			return (ListElement<V>) super.getTerminalElement(first);
		}

		@Override
		public ListElement<V> getElement(int index) throws IndexOutOfBoundsException {
			int remaining = index;
			for (MultiEntryHandle<K, V> entry : getMap().entrySet()) {
				int valueSize = entry.getValues().size();
				if (remaining < valueSize)
					return entryFor(entry.getElementId(), ((BetterList<V>) entry.getValues()).getElement(remaining));
				remaining -= valueSize;
			}
			throw new IndexOutOfBoundsException(index + " of " + (index - remaining));
		}

		@Override
		public ListElement<V> addElement(V value, ElementId after, ElementId before, boolean first)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<V>) super.addElement(value, after, before, first);
		}

		@Override
		public ListElement<V> move(ElementId valueEl, ElementId after, ElementId before, boolean first, Runnable afterRemove)
			throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<V>) super.move(valueEl, after, before, first, afterRemove);
		}

		@Override
		protected ListElement<V> entryFor(ElementId keyId, CollectionElement<V> valueEl) {
			return valueEl == null ? null : new OrderedValueElement(getMap().getEntryById(keyId), (ListElement<V>) valueEl);
		}

		@Override
		public MutableListElement<V> mutableElement(ElementId id) {
			if (!(id instanceof BetterMultiMapValueCollection.MapValueId))
				throw new NoSuchElementException();
			MapValueId mvi = (MapValueId) id;
			OrderedMultiEntry<K, V> entry = getMap().getEntryById(mvi.getKeyId());
			return new MutableOrderedValueElement(entry, (MutableListElement<V>) entry.getValues().mutableElement(mvi.getValueId()));
		}

		class OrderedValueElement extends ValueElement implements ListElement<V> {
			private final OrderedMultiEntry<K, V> theEntry;
			private final ListElement<V> theValueElement;

			OrderedValueElement(OrderedMultiEntry<K, V> entry, ListElement<V> valueEl) {
				this(getMap().getEntryById(entry.getElementId(), valueEl.getElementId()), entry, valueEl);
			}

			OrderedValueElement(MultiEntryValueHandle<K, V> kvEntry, OrderedMultiEntry<K, V> entry, ListElement<V> valueEl) {
				super(kvEntry);
				theEntry = entry;
				theValueElement = valueEl;
			}

			OrderedMultiEntry<K, V> getEntry() {
				return theEntry;
			}

			ListElement<V> getValueElement() {
				return theValueElement;
			}

			@Override
			public int getElementsBefore() {
				int count = theValueElement.getElementsBefore();
				for (OrderedMultiEntry<K, V> adjEntry = theEntry.getAdjacent(false); adjEntry != null; adjEntry = adjEntry
					.getAdjacent(false))
					count += adjEntry.getValues().size();
				return count;
			}

			@Override
			public int getElementsAfter() {
				int count = theValueElement.getElementsAfter();
				for (OrderedMultiEntry<K, V> adjEntry = theEntry.getAdjacent(true); adjEntry != null; adjEntry = adjEntry.getAdjacent(true))
					count += adjEntry.getValues().size();
				return count;
			}

			@Override
			public ListElement<V> getAdjacent(boolean next) {
				ListElement<V> adj = theValueElement.getAdjacent(next);
				OrderedMultiEntry<K, V> adjEntry = theEntry;
				while (adj == null && adjEntry != null) {
					adjEntry = adjEntry.getAdjacent(next);
					adj = adjEntry == null ? null : (ListElement<V>) adjEntry.getValues().getTerminalElement(next);
				}
				return adj == null ? null : new OrderedValueElement(adjEntry, adj);
			}
		}

		class MutableOrderedValueElement extends OrderedValueElement implements MutableListElement<V> {
			MutableOrderedValueElement(OrderedMultiEntry<K, V> entry, MutableListElement<V> valueEl) {
				super(getMap().mutableElement(entry.getElementId(), valueEl.getElementId()), entry, valueEl);
			}

			@Override
			MutableListElement<V> getValueElement() {
				return (MutableListElement<V>) super.getValueElement();
			}

			@Override
			protected MutableMultiMapHandle<K, V> getMapEntry() {
				return (MutableMultiMapHandle<K, V>) super.getMapEntry();
			}

			@Override
			public MutableListElement<V> getAdjacent(boolean next) {
				ListElement<V> adj = getValueElement().getAdjacent(next);
				OrderedMultiEntry<K, V> adjEntry = getEntry();
				while (adj == null && adjEntry != null) {
					adjEntry = adjEntry.getAdjacent(next);
					adj = (ListElement<V>) adjEntry.getValues().getTerminalElement(next);
				}
				if (adj != null && !(adj instanceof MutableListElement))
					adj = (ListElement<V>) adjEntry.getValues().mutableElement(adj.getElementId());
				return adj == null ? null : new MutableOrderedValueElement(adjEntry, (MutableListElement<V>) adj);
			}

			@Override
			public String isEnabled() {
				return getMapEntry().isEnabled();
			}

			@Override
			public String isAcceptable(V value) {
				return getMapEntry().isAcceptable(value);
			}

			@Override
			public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
				getMapEntry().set(value);
			}

			@Override
			public String canRemove() {
				return getMapEntry().canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				getMapEntry().remove();
			}
		}
	}

	/**
	 * Implements {@link ObservableMultiMap#singleEntries()}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ObservableSingleEntryCollection<K, V> extends BetterMapSingleEntryCollection<K, V>
	implements ObservableCollection<MultiEntryValueHandle<K, V>> {
		private Equivalence<? super Map.Entry<? extends K, ?>> theEquivalence;

		public ObservableSingleEntryCollection(ObservableMultiMap<K, V> map) {
			super(map);
		}

		@Override
		protected ObservableMultiMap<K, V> getMap() {
			return (ObservableMultiMap<K, V>) super.getMap();
		}

		@Override
		public ObservableSingleEntryCollection<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public boolean isEventing() {
			return getMap().isEventing();
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return getMap().getChangeSources();
		}

		@Override
		public Equivalence<? super MultiEntryValueHandle<K, V>> equivalence() {
			if (theEquivalence == null)
				theEquivalence = getMap().keySet().equivalence().map(null, key -> new SimpleMapEntry<>(key, null), Map.Entry::getKey);
			return theEquivalence;
		}

		@Override
		public boolean isContentControlled() {
			// We'll assume that if the key set is not content-controlled then the values won't be either
			return getMap().keySet().isContentControlled();
		}

		@Override
		public ListElement<MultiEntryValueHandle<K, V>> getElement(MultiEntryValueHandle<K, V> value, boolean first) {
			return (ListElement<MultiEntryValueHandle<K, V>>) super.getElement(value, first);
		}

		@Override
		public ListElement<MultiEntryValueHandle<K, V>> getElement(ElementId id) {
			return (ListElement<MultiEntryValueHandle<K, V>>) super.getElement(id);
		}

		@Override
		public ListElement<MultiEntryValueHandle<K, V>> getTerminalElement(boolean first) {
			return (ListElement<MultiEntryValueHandle<K, V>>) super.getTerminalElement(first);
		}

		@Override
		public ListElement<MultiEntryValueHandle<K, V>> getElement(int index) throws IndexOutOfBoundsException {
			CollectionElement<K> keyEl = getMap().keySet().getTerminalElement(true);
			int size = 0;
			while (keyEl != null) {
				MultiEntryHandle<K, V> entry = getMap().getEntryById(keyEl.getElementId());
				int entrySize = entry.getValues().size();
				if (size + entrySize > index)
					return entryFor(getMap().getEntryById(keyEl.getElementId(), getElement(entry.getValues(), index - size)));

				size += entrySize;
				keyEl = keyEl.getAdjacent(true);
			}
			throw new IndexOutOfBoundsException(index + " of " + size);
		}

		private ElementId getElement(BetterCollection<?> c, int index) {
			if (c instanceof BetterList)
				return ((BetterList<?>) c).getElement(index).getElementId();
			if (index <= c.size() / 2) {
				CollectionElement<?> id = c.getTerminalElement(true);
				for (int i = 0; i < index; i++)
					id = id.getAdjacent(true);
				return id.getElementId();
			} else {
				CollectionElement<?> id = c.getTerminalElement(false);
				for (int i = c.size() - 1; i > index; i--)
					id = id.getAdjacent(false);
				return id.getElementId();
			}
		}

		@Override
		public void setValue(Collection<ElementId> elements, MultiEntryValueHandle<K, V> value) {
			for (ElementId el : elements) {
				MutableCollectionElement<MultiEntryValueHandle<K, V>> entry = mutableElement(el);
				entry.set(value);
			}
		}

		@Override
		public ListElement<MultiEntryValueHandle<K, V>> addElement(MultiEntryValueHandle<K, V> value, ElementId after, ElementId before,
			boolean first) throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<MultiEntryValueHandle<K, V>>) super.addElement(value, after, before, first);
		}

		@Override
		public ListElement<MultiEntryValueHandle<K, V>> move(ElementId valueEl, ElementId after, ElementId before, boolean first,
			Runnable afterRemove) throws UnsupportedOperationException, IllegalArgumentException {
			return (ListElement<MultiEntryValueHandle<K, V>>) super.move(valueEl, after, before, first, afterRemove);
		}

		@Override
		public MutableListElement<MultiEntryValueHandle<K, V>> mutableElement(ElementId id) {
			return (MutableListElement<MultiEntryValueHandle<K, V>>) super.mutableElement(id);
		}

		@Override
		protected ListElement<MultiEntryValueHandle<K, V>> entryFor(MultiEntryValueHandle<K, V> entry) {
			return entry == null ? null : new ValueListElement(entry);
		}

		@Override
		protected MutableListElement<MultiEntryValueHandle<K, V>> mutableEntryFor(MultiEntryValueHandle<K, V> entry) {
			return new MutableValueListElement(entry);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends MultiEntryValueHandle<K, V>>> observer) {
			return getMap().onChange(mapEvt -> {
				MultiEntryValueHandle<K, V> entry;
				if (mapEvt.getKeyElement().isPresent() && mapEvt.getElementId() != null && mapEvt.getElementId().isPresent())
					entry = getMap().getEntryById(mapEvt.getKeyElement(), mapEvt.getElementId());
				else if (mapEvt.getElementId() == null) // key update
					return;
				else
					entry = new SyntheticEntry(mapEvt.getKeyElement(), mapEvt.getKey(), mapEvt.getElementId(), mapEvt.getOldValue());
				ListElement<MultiEntryValueHandle<K, V>> id = entryFor(entry);

				ObservableCollectionEvent<MultiEntryValueHandle<K, V>> collEvt = ObservableCollectionEvent.createCollectionEvent(//
					id.getElementId(), id.getElementsBefore(), mapEvt.getType(), //
					mapEvt.getType() == CollectionChangeType.add ? null : entry, entry, mapEvt, mapEvt.getMovement());
				try (Transaction evtT = collEvt.use()) {
					observer.accept(collEvt);
				}
			});
		}

		private class SyntheticEntry implements MultiEntryValueHandle<K, V> {
			private final ElementId theKeyId;
			private final K theKey;
			private final ElementId theValueId;
			private final V theValue;

			SyntheticEntry(ElementId keyId, K key, ElementId valueId, V value) {
				theKeyId = keyId;
				theKey = key;
				theValueId = valueId;
				theValue = value;
			}

			@Override
			public ElementId getKeyId() {
				return theKeyId;
			}

			@Override
			public K getKey() {
				return theKey;
			}

			@Override
			public ElementId getElementId() {
				return theValueId;
			}

			@Override
			public V get() {
				return theValue;
			}

			@Override
			public MultiEntryValueHandle<K, V> getAdjacent(boolean next) {
				return null;
			}
		}

		class ValueListElement extends ValueHandleElement implements ListElement<MultiEntryValueHandle<K, V>> {
			protected ValueListElement(MultiEntryValueHandle<K, V> entry) {
				super(entry);
			}

			@Override
			public int getElementsBefore() {
				return getElementCount(getEntry(), true);
			}

			@Override
			public int getElementsAfter() {
				return getElementCount(getEntry(), false);
			}

			@Override
			public ListElement<MultiEntryValueHandle<K, V>> getAdjacent(boolean next) {
				return (ListElement<MultiEntryValueHandle<K, V>>) super.getAdjacent(next);
			}
		}

		int getElementCount(MultiEntryValueHandle<K, V> entry, boolean before) {
			MultiEntryHandle<K, V> mapEntry = getMap().getEntryById(entry.getKeyId());
			int count;
			CollectionElement<V> valueEl = mapEntry.getValues().getElement(entry.getElementId());
			if (valueEl instanceof ListElement)
				count = before ? ((ListElement<?>) valueEl).getElementsBefore() : ((ListElement<?>) valueEl).getElementsAfter();
			else {
				count = 0;
				valueEl = valueEl.getAdjacent(!before);
				while (valueEl != null) {
					count++;
					valueEl = valueEl.getAdjacent(!before);
				}
			}
			MultiEntryHandle<K, V> keyEl = mapEntry.getAdjacent(!before);
			while (keyEl != null) {
				count += keyEl.getValues().size();
				keyEl.getAdjacent(false);
			}
			return count;
		}

		class MutableValueListElement extends MutableValueHandleElement implements MutableListElement<MultiEntryValueHandle<K, V>> {
			protected MutableValueListElement(MultiEntryValueHandle<K, V> entry) {
				super(entry);
			}

			@Override
			public int getElementsBefore() {
				return getElementCount(getEntry(), true);
			}

			@Override
			public int getElementsAfter() {
				return getElementCount(getEntry(), false);
			}

			@Override
			public MutableListElement<MultiEntryValueHandle<K, V>> getAdjacent(boolean next) {
				return (MutableListElement<MultiEntryValueHandle<K, V>>) super.getAdjacent(next);
			}
		}
	}

	/**
	 * Implements {@link ObservableMultiMap#reverse()}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ReversedObservableMultiMap<K, V> extends ReversedMultiMap<K, V> implements ObservableMultiMap<K, V> {
		public ReversedObservableMultiMap(ObservableMultiMap<K, V> source) {
			super(source);
		}

		@Override
		protected ObservableMultiMap<K, V> getSource() {
			return (ObservableMultiMap<K, V>) super.getSource();
		}

		@Override
		public ReversedObservableMultiMap<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public boolean isEventing() {
			return getSource().isEventing();
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return getSource().getChangeSources();
		}

		@Override
		public boolean isLockSupported() {
			return getSource().isLockSupported();
		}

		@Override
		public OrderedMultiEntry<K, V> getEntryById(ElementId keyId) {
			return OrderedMultiEntry.reverse(getSource().getEntryById(keyId));
		}

		@Override
		public ObservableSet<K> keySet() {
			return getSource().keySet().reverse();
		}

		@Override
		public ObservableSet<? extends MultiEntryHandle<K, V>> entrySet() {
			return getSource().entrySet().reverse();
		}

		@Override
		public ObservableMultiEntry<K, V> watchById(ElementId keyId) {
			return getSource().watchById(keyId).reverse();
		}

		@Override
		public ObservableMultiEntry<K, V> watch(K key) {
			return getSource().watch(key).reverse();
		}

		@Override
		public ObservableCollection<V> get(K key) {
			return getSource().get(key).reverse();
		}

		@Override
		public OrderedMultiEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends Iterable<? extends V>> value, ElementId afterKey,
			ElementId beforeKey, boolean first, Runnable preAdd, Runnable postAdd) {
			return OrderedMultiEntry.reverse(
				getSource().getOrPutEntry(key, value, ElementId.reverse(beforeKey), ElementId.reverse(afterKey), !first, preAdd, postAdd));
		}

		@Override
		public ObservableMultiMap<K, V> reverse() {
			return getSource();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>> action) {
			try (Transaction t = lock(false, null)) {
				return getSource().onChange(evt -> {
					int keySize = keySet().size();
					if (keySize == 0)
						keySize++; // May have just been removed
					int keyIndex = keySize - evt.getKeyIndex() - 1;
					int valueSize = get(evt.getKey()).size();
					if (valueSize == 0)
						valueSize++; // May have just been removed
					int valueIndex = valueSize - evt.getIndex() - 1;
					ObservableMultiMapEvent<K, V> event = new ObservableMultiMapEvent.Default<>(//
						evt.getKeyElement().reverse(), evt.getElementId().reverse(), keyIndex, valueIndex, evt.getType(), evt.getOldKey(),
						evt.getKey(), evt.getOldValue(), evt.getNewValue(), evt, evt.getMovement());
					try (Transaction mt = event.use()) {
						action.accept(event);
					}
				});
			}
		}

		@Override
		public MultiMapFlow<K, V> flow() {
			return getSource().flow().reverse();
		}
	}

	/**
	 * Implements {@link ObservableMultiMap#singleMap(boolean)}
	 *
	 * @param <K> The key-type of the map
	 * @param <V> The value-type of the map
	 */
	class ObservableSingleMap<K, V> extends SingleMap<K, V> implements ObservableMap<K, V> {
		public ObservableSingleMap(ObservableMultiMap<K, V> outer, boolean firstValue) {
			super(outer, firstValue);
		}

		@Override
		protected ObservableMultiMap<K, V> getSource() {
			return (ObservableMultiMap<K, V>) super.getSource();
		}

		@Override
		public ObservableSingleMap<K, V> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public boolean isEventing() {
			return getSource().isEventing();
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return getSource().getChangeSources();
		}

		@Override
		public ObservableSet<K> keySet() {
			return (ObservableSet<K>) super.keySet();
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public SettableElement<V> observe(K key) {
			return getSource().get(key).observeFind(v -> true).at(true).find();
		}

		@Override
		public OrderedMapEntry<K, V> getEntry(K key) {
			return (OrderedMapEntry<K, V>) super.getEntry(key);
		}

		@Override
		public OrderedMapEntry<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
			return (OrderedMapEntry<K, V>) super.putEntry(key, value, after, before, first);
		}

		@Override
		public OrderedMapEntry<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId afterKey, ElementId beforeKey,
			boolean first, Runnable preAdd, Runnable postAdd) {
			return (OrderedMapEntry<K, V>) super.getOrPutEntry(key, value, afterKey, beforeKey, first, preAdd, postAdd);
		}

		@Override
		public OrderedMapEntry<K, V> getEntryById(ElementId entryId) {
			return (OrderedMapEntry<K, V>) super.getEntryById(entryId);
		}

		@Override
		public MutableOrderedMapEntry<K, V> mutableEntry(ElementId entryId) {
			return (MutableOrderedMapEntry<K, V>) super.mutableEntry(entryId);
		}

		@Override
		protected OrderedMapEntry<K, V> entryFor(MultiEntryHandle<K, V> outerHandle) {
			return outerHandle == null ? null : new OrderedSingleEntry((OrderedMultiEntry<K, V>) outerHandle);
		}

		@Override
		protected MutableOrderedMapEntry<K, V> mutableEntryFor(MultiEntryHandle<K, V> outerHandle) {
			return outerHandle == null ? null : new MutableOrderedSingleEntry((OrderedMultiEntry<K, V>) outerHandle);
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			return getSource().onChange(multiMapEvt -> {
				int valueIndex = multiMapEvt.getIndex();
				if (isFirstValue()) {
					if (valueIndex > 0)
						return; // We only care about the first value for each key
				} else {
					int valueSize = getSource().getEntryById(multiMapEvt.getKeyElement()).getValues().size();
					boolean isTarget = false;
					switch (multiMapEvt.getType()) {
					case add:
					case set:
						isTarget = valueIndex == valueSize - 1;
						break;
					case remove:
						isTarget = valueIndex == valueSize;
						break;
					}
					if (!isTarget)
						return; // We only care about the last value for each key
				}
				ObservableMapEvent<K, V> mapEvt;
				if (multiMapEvt.getType() == CollectionChangeType.remove && multiMapEvt.getKeyElement().isPresent()) {
					V newValue = CollectionElement.get(//
						getSource().getEntryById(multiMapEvt.getKeyElement()).getValues().getTerminalElement(isFirstValue()));
					mapEvt = new ObservableMapEvent.MultiToSingleMapEvent<>(multiMapEvt, CollectionChangeType.set, newValue);
				} else {
					mapEvt = new ObservableMapEvent.MultiToSingleMapEvent<>(multiMapEvt, multiMapEvt.getType(), multiMapEvt.getNewValue());
				}
				try (Transaction evtT = mapEvt.use()) {
					action.accept(mapEvt);
				}
			});
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}

		protected class OrderedSingleEntry extends SingleEntry implements OrderedMapEntry<K, V> {
			protected OrderedSingleEntry(OrderedMultiEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected OrderedMultiEntry<K, V> getEntry() {
				return (OrderedMultiEntry<K, V>) super.getEntry();
			}

			@Override
			public OrderedMapEntry<K, V> getAdjacent(boolean next) {
				return entryFor(getEntry().getAdjacent(next));
			}

			@Override
			public int getElementsBefore() {
				return getEntry().getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return getEntry().getElementsAfter();
			}
		}

		protected class MutableOrderedSingleEntry extends MutableSingleEntry implements MutableOrderedMapEntry<K, V> {
			protected MutableOrderedSingleEntry(OrderedMultiEntry<K, V> entry) {
				super(entry);
			}

			@Override
			protected OrderedMultiEntry<K, V> getEntry() {
				return (OrderedMultiEntry<K, V>) super.getEntry();
			}

			@Override
			public MutableOrderedMapEntry<K, V> getAdjacent(boolean next) {
				return mutableEntryFor(getEntry().getAdjacent(next));
			}

			@Override
			public int getElementsBefore() {
				return getEntry().getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return getEntry().getElementsAfter();
			}
		}
	}

	/**
	 * Implements {@link ObservableMultiMap#observeSingleMap(BiFunction, Observable)}
	 *
	 * @param <K> The key-type of the maps (the source multi-map and this map)
	 * @param <V> The value-type of the source multi-map
	 * @param <X> The value type of this map
	 */
	class ActiveObservableSingleMap<K, V, X> extends AbstractIdentifiable implements ObservableMap<K, X> {
		private final ObservableMultiMap<K, V> theMultiMap;
		private final BiFunction<? super ObservableCollection<V>, ? super Observable<?>, ? extends SettableValue<X>> theValueProducer;
		private final ObservableSet<OrderedMultiEntry<K, V>> theMultiEntries;
		private final ObservableSet<MapEntryElement> theEntries;
		private final ObservableSet<K> theKeySet;
		private final ListenerList<Consumer<? super ObservableMapEvent<? extends K, ? extends X>>> theListeners;

		public ActiveObservableSingleMap(ObservableMultiMap<K, V> multiMap,
			BiFunction<? super ObservableCollection<V>, ? super Observable<?>, ? extends SettableValue<X>> valueProducer,
				Observable<?> until) {
			theMultiMap = multiMap;
			theValueProducer = valueProducer;
			theListeners = ListenerList.build().build();
			theMultiEntries = (ObservableSet<OrderedMultiEntry<K, V>>) theMultiMap.entrySet();
			// The purpose of using the Map.Entry type is to correctly handle the addition of entries
			ObservableSet<Map.Entry<K, X>> entries = theMultiEntries.flow()//
				.<Map.Entry<K, X>> transformEquivalent(tx -> tx.cache(true).reEvalOnUpdate(false).fireIfUnchanged(true)//
					.map(multiEntry -> {
						LightWeightObservable<Object> entryUntil = new LightWeightObservable<>();
						SettableValue<X> value = theValueProducer.apply(theMultiMap.get(multiEntry.getKey()), entryUntil);
						return new MapEntryElement(multiEntry, value, entryUntil);
					}).replaceMappingSourceWith((mapEntry, txvs) -> ((MapEntryElement) mapEntry).multiEntry, //
						reverse -> reverse.allowInexactReverse(true)//
						.rejectWith(entry -> {
							if (!(((Object) entry) instanceof ActiveObservableSingleMap.MapEntryElement))
								return "Can't add entries this way";
							else if (ActiveObservableSingleMap.this != ((MapEntryElement) entry).getMap())
								return "Can't add entries this way";
							return null;
						})//
						.rejectAddWith(entry -> {
							if (!(((Object) entry) instanceof ActiveObservableSingleMap.MapEntryElement))
								return "Can't add entries this way";
							else if (ActiveObservableSingleMap.this != ((MapEntryElement) entry).getMap())
								return "Can't add entries this way";
							return null;
						})))//
				.collectActive(until);
			theEntries = (ObservableSet<MapEntryElement>) (ObservableSet<?>) entries;
			theKeySet = theEntries.flow()//
				.<K> transformEquivalent(tx -> tx.cache(false)//
					.map(MapEntryElement::getKey)//
					.withReverse(key -> (MapEntryElement) getEntry(key)))//
				.collectPassive();
			Subscription valuesSub = theEntries.subscribe(evt -> {
				switch (evt.getType()) {
				case add:
					evt.getNewValue().theElement = theEntries.getElement(evt.getElementId());
					fire(new ObservableMapEvent.FromEntryEvent<>(evt));
					break;
				case remove:
					fire(new ObservableMapEvent.FromEntryEvent<>(evt));
					evt.getOldValue().unsubscribe(evt);
					break;
				case set:
					break; // Nothing to do--the value should do the work
				}
			}, true);
			if (until != null) {
				until.take(1).act(cause -> {
					List<MapEntryElement> toKill = QommonsUtils.unmodifiableCopy(theEntries);
					valuesSub.unsubscribe();
					for (MapEntryElement entry : toKill)
						entry.unsubscribe(cause);
				});
			}
		}

		@Override
		protected Object createIdentity() {
			return Identifiable.wrap(theMultiMap.getIdentity(), "single", theValueProducer);
		}

		@Override
		public ActiveObservableSingleMap<K, V, X> alias(String alias) {
			super.alias(alias);
			return this;
		}

		@Override
		public ObservableSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public ObservableSet<Map.Entry<K, X>> entrySet() {
			return (ObservableSet<Map.Entry<K, X>>) (ObservableSet<?>) theEntries;
		}

		@Override
		public boolean isEventing() {
			return theMultiMap.isEventing();
		}

		@Override
		public boolean isLockSupported() {
			return theMultiMap.isLockSupported();
		}

		@Override
		public Equivalence<? super X> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public CoreChangeSources getChangeSources() {
			return getChangeSources().getChangeSources();
		}

		@Override
		public OrderedMapEntry<K, X> getEntry(K key) {
			MultiEntryHandle<K, V> multiEntry = theMultiMap.getEntry(key);
			return multiEntry == null ? null : getEntryByMultiId(multiEntry.getElementId());
		}

		private OrderedMapEntry<K, X> getEntryByMultiId(ElementId multiId) {
			return CollectionElement.get(theEntries.getElementsBySource(multiId, theMultiEntries).peekFirst());
		}

		private ElementId unwrap(ElementId myId) {
			return myId == null ? null : theEntries.getElement(myId).get().getElementId();
		}

		private List<V> getInitialMultiValues(X value) {
			ObservableCollection<V> values = ObservableCollection.create();
			LightWeightObservable<Void> until = new LightWeightObservable<>();
			SettableValue<X> settableValue = theValueProducer.apply(values, until);
			settableValue.set(value);
			List<V> ret = QommonsUtils.unmodifiableCopy(values);
			until.onNext(null);
			return ret;
		}

		@Override
		public OrderedMapEntry<K, X> getOrPutEntry(K key, Function<? super K, ? extends X> value, ElementId after, ElementId before,
			boolean first, Runnable preAdd, Runnable postAdd) {
			MultiEntryHandle<K, V> multiEntry = theMultiMap.getOrPutEntry(key, k -> {
				return getInitialMultiValues(value.apply(k));
			}, unwrap(after), unwrap(before), first, preAdd, postAdd);
			return multiEntry == null ? null : getEntryByMultiId(multiEntry.getElementId());
		}

		@Override
		public OrderedMapEntry<K, X> getEntryById(ElementId entryId) {
			return theEntries.getElement(entryId).get();
		}

		@Override
		public MutableOrderedMapEntry<K, X> mutableEntry(ElementId entryId) {
			return new MutableEntry(theEntries.getElement(entryId).get());
		}

		@Override
		public String canPut(K key, X value) {
			ObservableCollection<V> values = ObservableCollection.create();
			LightWeightObservable<Void> until = new LightWeightObservable<>();
			SettableValue<X> settableValue = theValueProducer.apply(values, until);
			String msg = settableValue.isAcceptable(value);
			until.onNext(null);
			return msg;
		}

		private void fire(ObservableMapEvent<K, X> event) {
			try (Transaction t = event.use()) {
				theListeners.forEach(//
					l -> l.accept(event));
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends X>> action) {
			return theListeners.add(action, true)::run;
		}

		@Override
		public int hashCode() {
			// Copied and modified from AbstractMap
			int h = 0;
			Iterator<Map.Entry<K, X>> i = entrySet().iterator();
			while (i.hasNext()) {
				Entry<K, X> e = i.next();
				h += Objects.hashCode(e.getKey()) ^ Objects.hashCode(e.getValue());
			}
			return h;
		}

		@Override
		public boolean equals(Object o) {
			// Copied from AbstractMap
			if (o == this)
				return true;

			if (!(o instanceof Map))
				return false;
			Map<?, ?> m = (Map<?, ?>) o;
			if (m.size() != size())
				return false;

			try {
				Iterator<Map.Entry<K, X>> i = entrySet().iterator();
				while (i.hasNext()) {
					Map.Entry<K, X> e = i.next();
					K key = e.getKey();
					X value = e.getValue();
					if (value == null) {
						if (!(m.get(key) == null && m.containsKey(key)))
							return false;
					} else {
						if (!value.equals(m.get(key)))
							return false;
					}
				}
			} catch (ClassCastException unused) {
				return false;
			} catch (NullPointerException unused) {
				return false;
			}

			return true;
		}

		@Override
		public String toString() {
			return theEntries.toString();
		}

		class MapEntryElement implements OrderedMapEntry<K, X> {
			final OrderedMultiEntry<K, V> multiEntry;
			CollectionElement<MapEntryElement> theElement;
			final SettableValue<X> theValue;
			final LightWeightObservable<Object> theUntil;
			private Subscription theValueSub;
			X thePreviousValue;

			MapEntryElement(OrderedMultiEntry<K, V> multiEntry, SettableValue<X> value, LightWeightObservable<Object> until) {
				this.multiEntry = multiEntry;
				theValue = value;
				theValueSub = theValue.changes().act(evt -> {
					if (this.multiEntry.getElementId().isPresent())
						thePreviousValue = evt.getNewValue();
					else
						unsubscribe(evt);
				});
				theUntil = until;
			}

			ActiveObservableSingleMap<K, V, X> getMap() {
				return ActiveObservableSingleMap.this;
			}

			void unsubscribe(Object cause) {
				if (theValueSub != null) {
					theValueSub.unsubscribe();
					theValueSub = null;
					theUntil.onNext(cause);
				}
			}

			@Override
			public ElementId getElementId() {
				return theElement.getElementId();
			}

			@Override
			public K getKey() {
				return multiEntry.getKey();
			}

			@Override
			public X get() {
				return theValue.get();
			}

			@Override
			public int getElementsBefore() {
				return multiEntry.getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return multiEntry.getElementsAfter();
			}

			@Override
			public MapEntryElement getAdjacent(boolean next) {
				CollectionElement<MapEntryElement> adj = theElement.getAdjacent(next);
				return adj == null ? null : adj.get();
			}

			@Override
			public int hashCode() {
				return Objects.hashCode(getKey());
			}

			@Override
			public boolean equals(Object obj) {
				return obj == this;
			}

			@Override
			public String toString() {
				return getKey() + "=" + getValue();
			}
		}

		class MutableEntry implements MutableOrderedMapEntry<K, X> {
			private final MapEntryElement theElement;
			private final MutableCollectionElement<K> theMutableKeyEntry;

			MutableEntry(MapEntryElement element) {
				theElement = element;
				theMutableKeyEntry = theMultiMap.keySet().mutableElement(element.multiEntry.getElementId());
			}

			@Override
			public int getElementsBefore() {
				return theElement.getElementsBefore();
			}

			@Override
			public int getElementsAfter() {
				return theElement.getElementsAfter();
			}

			@Override
			public MutableOrderedMapEntry<K, X> getAdjacent(boolean next) {
				MapEntryElement adj = theElement.getAdjacent(next);
				return adj == null ? null : new MutableEntry(adj);
			}

			@Override
			public String isEnabled() {
				return theElement.theValue.isEnabled().get();
			}

			@Override
			public ElementId getElementId() {
				return theElement.getElementId();
			}

			@Override
			public K getKey() {
				return theElement.getKey();
			}

			@Override
			public X get() {
				return theElement.get();
			}

			@Override
			public String isAcceptable(X value) {
				return theElement.theValue.isAcceptable(value);
			}

			@Override
			public void set(X value) throws UnsupportedOperationException, IllegalArgumentException {
				theElement.theValue.isAcceptable(value);
			}

			@Override
			public String canRemove() {
				return theMutableKeyEntry.canRemove();
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				theMultiEntries.remove();
			}

			@Override
			public int hashCode() {
				return theElement.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof ActiveObservableSingleMap.MutableEntry && theElement.equals(((MutableEntry) obj).theElement);
			}

			@Override
			public String toString() {
				return theElement.toString();
			}
		}
	}

	/**
	 * Like {@link org.observe.collect.ObservableCollection.CollectionDataFlow CollectionDataFlow} for multi-maps. Provides different types
	 * of transformations and can be {@link #gather() gathered} into a ObservableMultiMap.
	 *
	 * @param <K> The key type of this flow
	 * @param <V> The value type of this flow
	 */
	interface MultiMapFlow<K, V> {
		/**
		 * @param <K2> The key type for the derived flow
		 * @param keyMap The function to produce a derived key flow from this flow's key flow
		 * @return The derived flow
		 */
		<K2> MultiMapFlow<K2, V> withKeys(Function<DistinctDataFlow<?, ?, K>, DistinctDataFlow<?, ?, K2>> keyMap);

		<K2> SortedMultiMapFlow<K2, V> withSortedKeys(Function<DistinctDataFlow<?, ?, K>, DistinctSortedDataFlow<?, ?, K2>> keyMap);

		/**
		 * @param <V2> The value type for the derived flow
		 * @param valueMap The function to produce a derived value flow from the value flow of each of this flow's entries' value
		 *        collections
		 * @return The derived flow
		 */
		<V2> MultiMapFlow<K, V2> withValues(Function<CollectionDataFlow<?, ?, V>, CollectionDataFlow<?, ?, V2>> valueMap);

		/**
		 * @param allowUpdates Whether to allow updates in the resulting map
		 * @return A flow that forbids modifications to the source map
		 */
		default MultiMapFlow<K, V> unmodifiable(boolean allowUpdates) {
			return withKeys(keys -> keys.unmodifiable(allowUpdates))//
				.withValues(values -> values.unmodifiable(allowUpdates));
		}

		/**
		 * @return A flow identical to this flow, but whose keys are reversed in the key set and whose values are reversed in each key's
		 *         value collection
		 */
		MultiMapFlow<K, V> reverse();

		/** @return Whether this flow supports passive (light-weight) gathering */
		boolean supportsPassive();

		/** @return Whether this flow both supports and prefers passive (light-weight) to active (heavy-weight) gathering */
		default boolean prefersPassive() {
			return supportsPassive();
		}

		/** @return An ObservableMultiMap derived from this flow's source by this flow's configuration */
		default ObservableMultiMap<K, V> gather() {
			if (prefersPassive())
				return gatherPassive();
			else
				return gatherActive(Observable.empty());
		}

		ObservableMultiMap<K, V> gatherPassive();

		/**
		 * @param until The observable to terminate the active map's listening (to its source data)
		 * @return An ObservableMultiMap derived from this flow's source by this flow's configuration
		 */
		ObservableMultiMap<K, V> gatherActive(Observable<?> until);
	}
}
