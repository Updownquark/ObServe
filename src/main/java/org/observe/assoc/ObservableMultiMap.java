package org.observe.assoc;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.assoc.ObservableSortedMultiMap.SortedMultiMapFlow;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.CollectionSubscription;
import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.DistinctSortedDataFlow;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableCollectionImpl.ReversedObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.SettableElement;
import org.observe.util.ObservableCollectionWrapper;
import org.observe.util.ObservableUtils.SubscriptionCause;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.MultiEntryValueHandle;
import org.qommons.collect.MultiMap;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.SimpleMapEntry;
import org.qommons.collect.SimpleMultiEntry;
import org.qommons.tree.BetterTreeMap;

import com.google.common.reflect.TypeToken;

/**
 * An observable map structure that allows more than one value to be stored per key
 *
 * @param <K> The type of key used by this map
 * @param <V> The type of values stored in this map
 */
public interface ObservableMultiMap<K, V> extends BetterMultiMap<K, V> {
	/** This class's wildcard {@link TypeToken} */
	static TypeToken<ObservableMap<?, ?>> TYPE = TypeTokens.get().keyFor(ObservableMap.class).wildCard();

	/** The wildcard {@link MultiEntryHandle} {@link TypeToken} */
	static TypeToken<MultiEntryHandle<?, ?>> ENTRY_TYPE = TypeTokens.get().keyFor(MultiEntryHandle.class).wildCard();

	/** The wildcard {@link MultiEntryValueHandle} {@link TypeToken} */
	static TypeToken<MultiEntryValueHandle<?, ?>> VALUE_ENTRY_TYPE = TypeTokens.get().keyFor(MultiEntryValueHandle.class).wildCard();

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

		static <K, V> ObservableMultiEntry<K, V> empty(K key, TypeToken<V> valueType) {
			return new EmptyMultiEntry<>(key, valueType);
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

			public EmptyMultiEntry(K key, TypeToken<V> valueType) {
				theKey = key;
				init(ObservableCollection.of(valueType));
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

	/** @return The type of keys this map uses */
	TypeToken<K> getKeyType();

	/** @return The type of values this map stores */
	TypeToken<V> getValueType();

	/** @return The type of elements in the entry set. Should be cached, as type tokens aren't cheap to build. */
	TypeToken<MultiEntryHandle<K, V>> getEntryType();

	/** @return The type of value elements in the map. Should be cached, as type tokens aren't cheap to build. */
	TypeToken<MultiEntryValueHandle<K, V>> getEntryValueType();

	/**
	 * Builds an {@link #getEntryType() entry type} from the key and value types
	 *
	 * @param keyType The key type of the map
	 * @param valueType The value type of the map
	 * @return The entry type for the map
	 */
	static <K, V> TypeToken<MultiEntryHandle<K, V>> buildEntryType(TypeToken<K> keyType, TypeToken<V> valueType) {
		return TypeTokens.get().keyFor(MultiEntryHandle.class).parameterized(keyType, valueType);
	}

	/**
	 * Builds an {@link #getEntryValueType() entry type} from the key and value types
	 *
	 * @param keyType The key type of the map
	 * @param valueType The value type of the map
	 * @return The entry type for the map
	 */
	static <K, V> TypeToken<MultiEntryValueHandle<K, V>> buildValueEntryType(TypeToken<K> keyType, TypeToken<V> valueType) {
		return TypeTokens.get().keyFor(MultiEntryValueHandle.class).parameterized(keyType, valueType);
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
	default ObservableCollection<V> get(Object key) {
		if (!keySet().belongs(key))
			return ObservableCollection.of(getValueType());
		return watch((K) key);
	}

	/**
	 * @param action The action to perform on changes to this map
	 * @return The collection subscription to terminate listening
	 */
	Subscription onChange(Consumer<? super ObservableMultiMapEvent<? extends K, ? extends V>> action);

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
			SubscriptionCause subCause = new SubscriptionCause();
			try (Transaction ct = SubscriptionCause.use(subCause)) {
				BetterSet<? extends MultiEntryHandle<K, V>> entrySet = entrySet();
				int keyIndex = keyForward ? 0 : entrySet.size() - 1;
				CollectionElement<? extends MultiEntryHandle<K, V>> entry = entrySet.getTerminalElement(keyForward);
				while (entry != null) {
					int valueIndex = valueForward ? 0 : entry.get().getValues().size() - 1;
					CollectionElement<V> value = entry.get().getValues().getTerminalElement(valueForward);
					while (value != null) {
						ObservableMultiMapEvent<K, V> mapEvent = new ObservableMultiMapEvent<>(entry.getElementId(), value.getElementId(),
							getKeyType(), getValueType(), keyIndex, valueIndex, CollectionChangeType.add, entry.get().getKey(), null,
							value.get(), subCause);
						try (Transaction mt = ObservableMultiMapEvent.use(mapEvent)) {
							action.accept(mapEvent);
						}
						entry = entrySet.getAdjacentElement(entry.getElementId(), valueForward);
						valueIndex += valueForward ? 1 : -1;
					}
					entry = entrySet.getAdjacentElement(entry.getElementId(), keyForward);
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
					SubscriptionCause unsubCause = new SubscriptionCause();
					try (Transaction ct = SubscriptionCause.use(unsubCause)) {
						BetterSet<? extends MultiEntryHandle<K, V>> entrySet = entrySet();
						int keyIndex = !keyForward ? 0 : entrySet.size() - 1;
						CollectionElement<? extends MultiEntryHandle<K, V>> entry = entrySet.getTerminalElement(!keyForward);
						while (entry != null) {
							int valueIndex = !valueForward ? 0 : entry.get().getValues().size() - 1;
							CollectionElement<V> value = entry.get().getValues().getTerminalElement(!valueForward);
							while (value != null) {
								ObservableMultiMapEvent<K, V> mapEvent = new ObservableMultiMapEvent<>(entry.getElementId(),
									value.getElementId(), getKeyType(), getValueType(), keyIndex, valueIndex, CollectionChangeType.remove,
									entry.get().getKey(), value.get(), value.get(), subCause);
								try (Transaction mt = ObservableMultiMapEvent.use(mapEvent)) {
									action.accept(mapEvent);
								}
								entry = entrySet.getAdjacentElement(entry.getElementId(), !valueForward);
								valueIndex += !valueForward ? 1 : -1;
							}
							entry = entrySet.getAdjacentElement(entry.getElementId(), !keyForward);
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
	 * @return A builder to build a map whose values are a combination of all values in this map for the same key
	 */
	default <X> CombinedObservableSingleMapBuilder<K, V, X> observeSingleMap(TypeToken<X> valueType,
		Function<? super ObservableCollection<? extends V>, ? extends ObservableValue<? extends X>> combination) {
		return new CombinedObservableSingleMapBuilder<>(this, valueType, combination);
	}

	/**
	 * @return An observable that fires a value whenever anything in this structure changes. This observable will only fire 1 event per
	 *         transaction.
	 */
	default Observable<Object> changes() {
		return entrySet().simpleChanges();
	}

	/** @return A multi-map data flow that may be used to produce derived maps whose data is based on this map's */
	MultiMapFlow<K, V> flow();

	/**
	 * Builds a basic {@link ObservableMultiMap}
	 *
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param keyType The key type for the map
	 * @param valueType The value type for the map
	 * @return A builder to build a new {@link ObservableMultiMap}
	 */
	public static <K, V> Builder<K, V> build(Class<K> keyType, Class<V> valueType) {
		return build(TypeTokens.get().of(keyType), TypeTokens.get().of(valueType));
	}

	/**
	 * Builds a basic {@link ObservableMultiMap}
	 *
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param keyType The key type for the map
	 * @param valueType The value type for the map
	 * @return A builder to build a new {@link ObservableMultiMap}
	 */
	public static <K, V> Builder<K, V> build(TypeToken<K> keyType, TypeToken<V> valueType) {
		return new Builder<>(null, keyType, valueType, "ObservableMultiMap");
	}

	/**
	 * Builds a basic {@link ObservableMultiMap}
	 *
	 * @param <K> The key type for the map
	 * @param <V> the value type for the map
	 */
	class Builder<K, V> {
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

		@SuppressWarnings("rawtypes")
		private static final TypeToken<MapEntry> INNER_ENTRY_TYPE = TypeTokens.get().of(MapEntry.class);

		private final TypeToken<K> theKeyType;
		private final TypeToken<V> theValueType;
		private final DefaultObservableCollection.Builder<MapEntry<K, V>, ?> theBackingBuilder;
		private Equivalence<? super K> theKeyEquivalence;
		private Equivalence<? super V> theValueEquivalence;
		private String theDescription;

		Builder(DefaultObservableCollection.Builder<MapEntry<K, V>, ?> backingBuilder, //
			TypeToken<K> keyType, TypeToken<V> valueType, String defaultDescrip) {
			theKeyType = keyType;
			theValueType = valueType;
			if (backingBuilder == null)
				backingBuilder = (DefaultObservableCollection.Builder<MapEntry<K, V>, ?>) // Type hackery for performance reasons
				(DefaultObservableCollection.Builder<?, ?>) //
				DefaultObservableCollection.build(INNER_ENTRY_TYPE);
			theBackingBuilder = backingBuilder;
			theKeyEquivalence = Equivalence.DEFAULT;
			theValueEquivalence = Equivalence.DEFAULT;
			theDescription = defaultDescrip;
		}

		/**
		 * @param safe Whether the map should be thread-safe
		 * @return This builder
		 */
		public Builder<K, V> safe(boolean safe) {
			theBackingBuilder.safe(safe);
			return this;
		}

		/**
		 * @param locking The locking strategy for the map
		 * @return This builder
		 */
		public Builder<K, V> withLocker(CollectionLockingStrategy locking) {
			theBackingBuilder.withLocker(locking);
			return this;
		}

		/**
		 * @param sorting The sorting for the key set
		 * @return A sorted builder with the same settings as this builder but that will build an {@link ObservableSortedMultiMap}
		 */
		public ObservableSortedMultiMap.Builder<K, V> sortedBy(Comparator<? super K> sorting) {
			return new ObservableSortedMultiMap.Builder<>(theBackingBuilder, theKeyType, theValueType, sorting, theDescription);
		}

		/**
		 * @param keyEquivalence The key equivalence for the multi-map
		 * @return This builder, or if this builder is {@link #sortedBy(Comparator) sorted}, a new builder with the same settings as this
		 *         one but the given key equivalence
		 */
		public Builder<K, V> withKeyEquivalence(Equivalence<? super K> keyEquivalence) {
			theKeyEquivalence = keyEquivalence;
			return this;
		}

		/**
		 * @param valueEquivalence The value equivalence for the multi-map
		 * @return This builder
		 */
		public Builder<K, V> withValueEquivalence(Equivalence<? super V> valueEquivalence) {
			theValueEquivalence = valueEquivalence;
			return this;
		}

		/**
		 * @param description The description for the multi-map's {@link Identifiable#getIdentity() identity}
		 * @return This builder
		 */
		public Builder<K, V> withDescription(String description) {
			theDescription = description;
			return this;
		}

		protected Equivalence<? super V> getValueEquivalence() {
			return theValueEquivalence;
		}

		protected void setValueEquivalence(Equivalence<? super V> valueEquivalence) {
			theValueEquivalence = valueEquivalence;
		}

		protected String getDescrip() {
			return theDescription;
		}

		protected TypeToken<K> getKeyType() {
			return theKeyType;
		}

		protected TypeToken<V> getValueType() {
			return theValueType;
		}

		protected DefaultObservableCollection.Builder<MapEntry<K, V>, ?> getBackingBuilder() {
			return theBackingBuilder;
		}

		/**
		 * @param until The observable that, when it fires, will release all of the gathered multi-map's resources
		 * @return The new multi-map
		 */
		public ObservableMultiMap<K, V> build(Observable<?> until) {
			ObservableCollection<MapEntry<K, V>> backing = theBackingBuilder.withDescription(theDescription + " backing").build();
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
			if (theKeyEquivalence instanceof Equivalence.ComparatorEquivalence) {
				mapFlow = backing.flow().groupSorted(
					entries -> entries.transform(theKeyType, tx -> tx.map(keyMap).modifySource(keySet))//
					.distinctSorted(((Equivalence.ComparatorEquivalence<K>) theKeyEquivalence).comparator(), true), //
					keyReverse);
			} else {
				mapFlow = backing.flow()
					.groupBy(
						entries -> entries
						.transform(theKeyType, tx -> tx.map(keyMap).modifySource(keySet).withEquivalence(theKeyEquivalence)).distinct(),
						keyReverse);
			}
			return mapFlow
				.withValues(entries -> entries.transform(theValueType, //
					tx -> tx.map(valueMap)
					.modifySource(valueSet, //
						rvrs -> rvrs.createWith(addition))//
					.withEquivalence(theValueEquivalence)))//
				.gatherActive(until);
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
		public TypeToken<MultiEntryHandle<K, V>> getType() {
			return getMap().getEntryType();
		}

		@Override
		public Equivalence<? super MultiEntryHandle<K, V>> equivalence() {
			if (theEquivalence == null)
				theEquivalence = getMap().keySet().equivalence().map(
					(Class<MultiMap.MultiEntry<? extends K, ?>>) (Class<?>) MultiMap.MultiEntry.class, null,
					key -> new SimpleMultiEntry<>(key, false), MultiMap.MultiEntry::getKey);
			return theEquivalence;
		}

		@Override
		public boolean isContentControlled() {
			return getMap().keySet().isContentControlled();
		}

		@Override
		public int getElementsBefore(ElementId id) {
			return getMap().keySet().getElementsBefore(id);
		}

		@Override
		public int getElementsAfter(ElementId id) {
			return getMap().keySet().getElementsAfter(id);
		}

		@Override
		public CollectionElement<MultiEntryHandle<K, V>> getElement(int index) throws IndexOutOfBoundsException {
			return entryFor(getMap().getEntryById(getMap().keySet().getElement(index).getElementId()));
		}

		@Override
		public void setValue(Collection<ElementId> elements, MultiEntryHandle<K, V> value) {
			if (!elements.isEmpty())
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
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
				else if (mapEvt.getType() == CollectionChangeType.remove && !mapEvt.getKeyElement().isPresent())
					changeType = CollectionChangeType.remove;
				else
					changeType = CollectionChangeType.set;

				ObservableCollectionEvent<MultiEntryHandle<K, V>> collEvt = new ObservableCollectionEvent<>(//
					mapEvt.getKeyElement(), getType(), mapEvt.getKeyIndex(), changeType, //
					changeType == CollectionChangeType.add ? null : entry, entry, mapEvt);
				try (Transaction evtT = Causable.use(collEvt)) {
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
			public BetterCollection<V> getValues() {
				return BetterList.empty();
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
		public TypeToken<MultiEntryValueHandle<K, V>> getType() {
			return getMap().getEntryValueType();
		}

		@Override
		public Equivalence<? super MultiEntryValueHandle<K, V>> equivalence() {
			if (theEquivalence == null)
				theEquivalence = getMap().keySet().equivalence().map((Class<Map.Entry<? extends K, ?>>) (Class<?>) Map.Entry.class, null,
					key -> new SimpleMapEntry<>(key, null), Map.Entry::getKey);
			return theEquivalence;
		}

		@Override
		public boolean isContentControlled() {
			// We'll assume that if the key set is not content-controlled then the values won't be either
			return getMap().keySet().isContentControlled();
		}

		@Override
		public int getElementsBefore(ElementId id) {
			MultiEntryValueHandle<K, V> handle = getElement(id).get();
			MultiEntryHandle<K, V> entry = getMap().getEntryById(handle.getKeyId());
			int before = getElements(entry.getValues(), id, true);
			CollectionElement<K> keyEl = getMap().keySet().getAdjacentElement(entry.getElementId(), false);
			while (keyEl != null) {
				before += getMap().getEntryById(keyEl.getElementId()).getValues().size();
				keyEl = getMap().keySet().getAdjacentElement(keyEl.getElementId(), false);
			}
			return before;
		}

		@Override
		public int getElementsAfter(ElementId id) {
			MultiEntryValueHandle<K, V> handle = getElement(id).get();
			MultiEntryHandle<K, V> entry = getMap().getEntryById(handle.getKeyId());
			int after = getElements(entry.getValues(), id, false);
			CollectionElement<K> keyEl = getMap().keySet().getAdjacentElement(entry.getElementId(), true);
			while (keyEl != null) {
				after += getMap().getEntryById(keyEl.getElementId()).getValues().size();
				keyEl = getMap().keySet().getAdjacentElement(keyEl.getElementId(), true);
			}
			return after;
		}

		private int getElements(BetterCollection<?> c, ElementId id, boolean before) {
			if (c instanceof BetterList)
				return ((BetterList<?>) c).getElementsBefore(id);
			CollectionElement<?> el = c.getAdjacentElement(id, !before);
			int elements = 0;
			while (el != null) {
				elements++;
				el = c.getAdjacentElement(el.getElementId(), !before);
			}
			return elements;
		}

		@Override
		public CollectionElement<MultiEntryValueHandle<K, V>> getElement(int index) throws IndexOutOfBoundsException {
			CollectionElement<K> keyEl = getMap().keySet().getTerminalElement(true);
			int size = 0;
			while (keyEl != null) {
				MultiEntryHandle<K, V> entry = getMap().getEntryById(keyEl.getElementId());
				int entrySize = entry.getValues().size();
				if (size + entrySize > index)
					return entryFor(getMap().getEntryById(keyEl.getElementId(), getElement(entry.getValues(), index - size)));

				size += entrySize;
				keyEl = getMap().keySet().getAdjacentElement(keyEl.getElementId(), true);
			}
			throw new IndexOutOfBoundsException(index + " of " + size);
		}

		private ElementId getElement(BetterCollection<?> c, int index) {
			if (c instanceof BetterList)
				return ((BetterList<?>) c).getElement(index).getElementId();
			if (index <= c.size() / 2) {
				ElementId id = c.getTerminalElement(true).getElementId();
				for (int i = 0; i < index; i++)
					id = c.getAdjacentElement(id, true).getElementId();
				return id;
			} else {
				ElementId id = c.getTerminalElement(false).getElementId();
				for (int i = c.size() - 1; i > index; i--)
					id = c.getAdjacentElement(id, false).getElementId();
				return id;
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
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends MultiEntryValueHandle<K, V>>> observer) {
			return getMap().onChange(mapEvt -> {
				MultiEntryValueHandle<K, V> entry;
				if (mapEvt.getKeyElement().isPresent() && mapEvt.getElementId().isPresent())
					entry = getMap().getEntryById(mapEvt.getKeyElement(), mapEvt.getElementId());
				else
					entry = new SyntheticEntry(mapEvt.getKeyElement(), mapEvt.getKey(), mapEvt.getElementId(), mapEvt.getOldValue());
				ElementId id = entryFor(entry).getElementId();

				ObservableCollectionEvent<MultiEntryValueHandle<K, V>> collEvt = new ObservableCollectionEvent<>(//
					id, getType(), getElementsBefore(id), mapEvt.getType(), //
					mapEvt.getType() == CollectionChangeType.add ? null : entry, entry, mapEvt);
				try (Transaction evtT = Causable.use(collEvt)) {
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
		public TypeToken<K> getKeyType() {
			return getSource().getKeyType();
		}

		@Override
		public TypeToken<V> getValueType() {
			return getSource().getValueType();
		}

		@Override
		public TypeToken<MultiEntryHandle<K, V>> getEntryType() {
			return getSource().getEntryType();
		}

		@Override
		public TypeToken<MultiEntryValueHandle<K, V>> getEntryValueType() {
			return getSource().getEntryValueType();
		}

		@Override
		public boolean isLockSupported() {
			return getSource().isLockSupported();
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
		public ObservableCollection<V> get(Object key) {
			return getSource().get(key).reverse();
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
					ObservableMultiMapEvent<K, V> event = new ObservableMultiMapEvent<>(//
						evt.getKeyElement().reverse(), evt.getElementId().reverse(), getSource().getKeyType(), getSource().getValueType(), //
						keyIndex, valueIndex, evt.getType(), evt.getKey(), evt.getOldValue(), evt.getNewValue(), evt);
					try (Transaction mt = ObservableMultiMapEvent.use(event)) {
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
		private final TypeToken<Map.Entry<K, V>> theEntryType;

		public ObservableSingleMap(ObservableMultiMap<K, V> outer, boolean firstValue) {
			super(outer, firstValue);
			theEntryType = ObservableMap.buildEntryType(getKeyType(), getValueType());
		}

		@Override
		protected ObservableMultiMap<K, V> getSource() {
			return (ObservableMultiMap<K, V>) super.getSource();
		}

		@Override
		public ObservableSet<K> keySet() {
			return (ObservableSet<K>) super.keySet();
		}

		@Override
		public TypeToken<K> getKeyType() {
			return getSource().getKeyType();
		}

		@Override
		public TypeToken<V> getValueType() {
			return getSource().getValueType();
		}

		@Override
		public TypeToken<Map.Entry<K, V>> getEntryType() {
			return theEntryType;
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public <K2> SettableElement<V> observe(K2 key) {
			return getSource().get(key).observeFind(v -> true).at(true).find();
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
					mapEvt = new ObservableMapEvent<>(multiMapEvt.getKeyElement(), getKeyType(), getValueType(), multiMapEvt.getKeyIndex(), //
						CollectionChangeType.set, multiMapEvt.getKey(), multiMapEvt.getOldValue(), newValue, multiMapEvt);
				} else {
					mapEvt = new ObservableMapEvent<>(multiMapEvt.getKeyElement(), getKeyType(), getValueType(), multiMapEvt.getKeyIndex(), //
						multiMapEvt.getType(), multiMapEvt.getKey(), multiMapEvt.getOldValue(), multiMapEvt.getNewValue(), multiMapEvt);
				}
				try (Transaction evtT = Causable.use(mapEvt)) {
					action.accept(mapEvt);
				}
			});
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}

	/**
	 * Builds a {@link BetterMap} whose values are a combination of all values of the same key from a {@link BetterMultiMap}
	 *
	 * @param <K> The key type of both maps
	 * @param <V> The value type of the multi-map
	 * @param <X> The value type of the target map
	 */
	class CombinedObservableSingleMapBuilder<K, V, X> extends CombinedSingleMapBuilder<K, V, X> {
		private final Function<? super ObservableCollection<? extends V>, ? extends ObservableValue<? extends X>> theObservableCombination;
		private final TypeToken<X> theValueType;

		CombinedObservableSingleMapBuilder(ObservableMultiMap<K, V> source, TypeToken<X> valueType,
			Function<? super ObservableCollection<? extends V>, ? extends ObservableValue<? extends X>> combination) {
			super(source,
				collection -> combination.apply(ObservableCollection.create(source.getValueType(), (BetterList<V>) collection)).get());
			theObservableCombination = combination;
			theValueType = valueType;
		}

		@Override
		protected ObservableMultiMap<K, V> getSource() {
			return (ObservableMultiMap<K, V>) super.getSource();
		}

		@Override
		public CombinedObservableSingleMapBuilder<K, V, X> withReverse(
			BiFunction<? super BetterCollection<? extends V>, ? super X, ? extends X> reverse) {
			super.withReverse(reverse);
			return this;
		}

		@Override
		public CombinedObservableSingleMapBuilder<K, V, X> withReversibility(
			Function<? super BetterCollection<? extends V>, String> reversibilityQuery) {
			super.withReversibility(reversibilityQuery);
			return this;
		}

		@Override
		public CombinedObservableSingleMapBuilder<K, V, X> withValuedReversibility(
			BiFunction<? super BetterCollection<? extends V>, ? super X, String> valuedReversibilityQuery) {
			super.withValuedReversibility(valuedReversibilityQuery);
			return this;
		}

		protected Function<? super ObservableCollection<? extends V>, ? extends ObservableValue<? extends X>> getObservableCombination() {
			return theObservableCombination;
		}

		@Override
		public ObservableMap<K, X> build() {
			return new CombinedObservableSingleMap<>(getSource(), theValueType, getCombination(), theObservableCombination, getReverse(),
				getReversibilityQuery(), getValuedReversibilityQuery());
		}
	}

	/**
	 * Implements the map built by {@link ObservableMultiMap#observeSingleMap(TypeToken, Function)}
	 *
	 * @param <K> The key type of both maps
	 * @param <V> The value type of the source multi-map
	 * @param <X> The value type of this map
	 */
	class CombinedObservableSingleMap<K, V, X> extends CombinedSingleMap<K, V, X> implements ObservableMap<K, X> {
		private final TypeToken<X> theValueType;
		private final Function<? super ObservableCollection<? extends V>, ? extends ObservableValue<? extends X>> theObservableCombination;

		private TypeToken<Map.Entry<K, X>> theEntryType;

		CombinedObservableSingleMap(ObservableMultiMap<K, V> outer, TypeToken<X> valueType,
			Function<? super BetterCollection<? extends V>, ? extends X> combination,
			Function<? super ObservableCollection<? extends V>, ? extends ObservableValue<? extends X>> observableCombination,
				BiFunction<? super BetterCollection<? extends V>, ? super X, ? extends X> reverse,
				Function<? super BetterCollection<? extends V>, String> reversibility,
				BiFunction<? super BetterCollection<? extends V>, ? super X, String> valuedReversibility) {
			super(outer, combination, reverse, reversibility, valuedReversibility);
			theValueType = valueType;
			theObservableCombination = observableCombination;
		}

		@Override
		protected ObservableMultiMap<K, V> getSource() {
			return (ObservableMultiMap<K, V>) super.getSource();
		}

		@Override
		public ObservableSet<K> keySet() {
			return (ObservableSet<K>) super.keySet();
		}

		@Override
		public TypeToken<K> getKeyType() {
			return getSource().getKeyType();
		}

		@Override
		public TypeToken<X> getValueType() {
			return theValueType;
		}

		@Override
		public TypeToken<Map.Entry<K, X>> getEntryType() {
			if (theEntryType == null)
				theEntryType = ObservableMap.buildEntryType(getKeyType(), getValueType());
			return theEntryType;
		}

		@Override
		public Equivalence<? super X> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public <K2> SettableElement<X> observe(K2 key) {
			if (keySet().belongs(key))
				return SettableElement.empty(getValueType());
			ObservableMultiEntry<K, V> sourceValues = getSource().watch((K) key);
			ObservableValue<? extends X> combined = theObservableCombination.apply(sourceValues);
			class CombinedElement extends AbstractIdentifiable implements SettableElement<X> {
				@Override
				public Object createIdentity() {
					return Identifiable.wrap(CombinedObservableSingleMap.this.getIdentity(), "value", key);
				}

				@Override
				public TypeToken<X> getType() {
					return getValueType();
				}

				@Override
				public long getStamp() {
					return sourceValues.getStamp();
				}

				@Override
				public ElementId getElementId() {
					return sourceValues.getKeyId();
				}

				@Override
				public boolean isLockSupported() {
					return getSource().isLockSupported();
				}

				@Override
				public Transaction lock(boolean write, Object cause) {
					return getSource().lock(write, cause);
				}

				@Override
				public Transaction tryLock(boolean write, Object cause) {
					return getSource().tryLock(write, cause);
				}

				@Override
				public X get() {
					return combined.get();
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return ObservableValue.of(TypeTokens.get().STRING, //
						() -> canReverse(sourceValues), //
						sourceValues::getStamp, sourceValues.simpleChanges());
				}

				@Override
				public <X2 extends X> String isAcceptable(X2 value) {
					return canReverse(sourceValues, value);
				}

				@Override
				public <X2 extends X> X set(X2 value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
					try (Transaction t = CombinedObservableSingleMap.this.lock(true, cause)) {
						return reverse(sourceValues, value);
					}
				}

				@Override
				public Observable<ObservableElementEvent<X>> elementChanges() {
					ElementId[] id = new ElementId[1];
					return combined.changes().map(valueEvent -> {
						ElementId oldId = id[0];
						id[0] = sourceValues.getKeyId();
						ObservableElementEvent<X> elementEvent = new ObservableElementEvent<>(getValueType(), valueEvent.isInitial(), oldId,
							id[0], valueEvent.getOldValue(), valueEvent.getNewValue(), valueEvent);
						Transaction elEvtFinish = Causable.use(elementEvent);
						valueEvent.onFinish(Causable.key((__, ___) -> elEvtFinish.close()));
						return elementEvent;
					});
				}
			}
			return new CombinedElement();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends X>> action) {
			return subscribe(action, true, false);
		}

		private Subscription subscribe(Consumer<? super ObservableMapEvent<? extends K, ? extends X>> action, boolean forward,
			boolean populate) {
			class MapEntry {
				final ObservableCollection<V> values;
				final Subscription sub;
				X value;

				MapEntry(ObservableMultiEntry<K, V> entry) {
					values = ObservableCollection.build(getSource().getValueType()).safe(false).build();
					values.addAll(entry);
					sub = theObservableCombination.apply(values).changes().act(evt -> value = evt.getNewValue());
				}
			}
			BetterSortedMap<ElementId, MapEntry> entries = new BetterTreeMap<>(false, ElementId::compareTo);
			try (Transaction t = getSource().lock(false, null)) {
				if (!populate) {
					for (CollectionElement<K> keyEl : getSource().keySet().elements())
						entries.put(keyEl.getElementId(), new MapEntry(getSource().watchById(keyEl.getElementId())));
				}
				Consumer<ObservableMultiMapEvent<? extends K, ? extends V>> sourceChange = multiEvent -> {
					boolean[] added = new boolean[1];
					MapEntryHandle<ElementId, MapEntry> entry = entries.getOrPutEntry(multiEvent.getKeyElement(),
						ki -> new MapEntry(getSource().watchById(ki)), null, null, false, () -> added[0] = true);
					ObservableMapEvent<K, X> mapEvent;
					if (added[0])
						mapEvent = new ObservableMapEvent<>(multiEvent.getKeyElement(), getKeyType(), getValueType(),
							multiEvent.getKeyIndex(), CollectionChangeType.add, multiEvent.getKey(), null, entry.get().value, multiEvent);
					else {
						X oldValue = entry.get().value;
						boolean removed = false;
						switch (multiEvent.getType()) {
						case add:
							entry.get().values.add(multiEvent.getIndex(), multiEvent.getNewValue());
							break;
						case remove:
							entry.get().values.remove(multiEvent.getIndex());
							removed = entry.get().values.isEmpty();
							break;
						case set:
							entry.get().values.set(multiEvent.getIndex(), multiEvent.getNewValue());
							break;
						}
						if (removed) {
							entry.get().sub.unsubscribe();
							entries.mutableEntry(entry.getElementId()).remove();
							mapEvent = new ObservableMapEvent<>(multiEvent.getKeyElement(), getKeyType(), getValueType(),
								multiEvent.getKeyIndex(), CollectionChangeType.remove, multiEvent.getKey(), oldValue, oldValue, multiEvent);
						} else
							mapEvent = new ObservableMapEvent<>(multiEvent.getKeyElement(), getKeyType(), getValueType(),
								multiEvent.getKeyIndex(), CollectionChangeType.set, multiEvent.getKey(), oldValue, entry.get().value,
								multiEvent);
					}
					try (Transaction evtT = Causable.use(mapEvent)) {
						action.accept(mapEvent);
					}
				};
				return populate ? getSource().subscribe(sourceChange, forward, forward) : getSource().onChange(sourceChange);
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
