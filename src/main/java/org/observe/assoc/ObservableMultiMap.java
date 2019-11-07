package org.observe.assoc;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.CollectionSubscription;
import org.observe.collect.Equivalence;
import org.observe.collect.FlowOptions.GroupingOptions;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSet;
import org.observe.collect.SettableElement;
import org.observe.util.ObservableUtils.SubscriptionCause;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMultiMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.MultiEntryValueHandle;
import org.qommons.collect.MultiMap;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.SimpleMapEntry;
import org.qommons.collect.SimpleMultiEntry;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * An observable map structure that allows more than one value to be stored per key
 *
 * @param <K> The type of key used by this map
 * @param <V> The type of values stored in this map
 */
public interface ObservableMultiMap<K, V> extends BetterMultiMap<K, V> {
	/** This class's type key */
	@SuppressWarnings("rawtypes")
	static TypeTokens.TypeKey<ObservableMultiMap> TYPE_KEY = TypeTokens.get().keyFor(ObservableMultiMap.class)
	.enableCompoundTypes(new TypeTokens.BinaryCompoundTypeCreator<ObservableMultiMap>() {
		@Override
		public <P1, P2> TypeToken<? extends ObservableMultiMap> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2) {
			return new TypeToken<ObservableMultiMap<P1, P2>>() {}.where(new TypeParameter<P1>() {}, param1)
				.where(new TypeParameter<P2>() {}, param2);
		}
	});
	/** This class's wildcard {@link TypeToken} */
	static TypeToken<ObservableMultiMap<?, ?>> TYPE = TYPE_KEY.parameterized();

	/** This type key for {@link MultiEntryHandle} */
	@SuppressWarnings("rawtypes")
	static TypeTokens.TypeKey<MultiEntryHandle> ENTRY_KEY = TypeTokens.get().keyFor(MultiEntryHandle.class)
	.enableCompoundTypes(new TypeTokens.BinaryCompoundTypeCreator<MultiEntryHandle>() {
		@Override
		public <P1, P2> TypeToken<? extends MultiEntryHandle> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2) {
			return new TypeToken<MultiEntryHandle<P1, P2>>() {}.where(new TypeParameter<P1>() {}, param1)
				.where(new TypeParameter<P2>() {}, param2);
		}
	});
	/** This wildcard {@link TypeToken} for {@link MultiEntryHandle} */
	static TypeToken<MultiEntryHandle<?, ?>> ENTRY_TYPE = ENTRY_KEY.parameterized();

	/** This type key for {@link MultiEntryValueHandle} */
	@SuppressWarnings("rawtypes")
	static TypeTokens.TypeKey<MultiEntryValueHandle> VALUE_ENTRY_KEY = TypeTokens.get().keyFor(MultiEntryValueHandle.class)
	.enableCompoundTypes(new TypeTokens.BinaryCompoundTypeCreator<MultiEntryValueHandle>() {
		@Override
		public <P1, P2> TypeToken<? extends MultiEntryValueHandle> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2) {
			return new TypeToken<MultiEntryValueHandle<P1, P2>>() {}.where(new TypeParameter<P1>() {}, param1)
				.where(new TypeParameter<P2>() {}, param2);
		}
	});
	/** This wildcard {@link TypeToken} for {@link MultiEntryValueHandle} */
	static TypeToken<MultiEntryValueHandle<?, ?>> VALUE_ENTRY_TYPE = VALUE_ENTRY_KEY.parameterized();

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
		return ENTRY_KEY.getCompoundType(keyType, valueType);
	}

	/**
	 * Builds an {@link #getEntryValueType() entry type} from the key and value types
	 *
	 * @param keyType The key type of the map
	 * @param valueType The value type of the map
	 * @return The entry type for the map
	 */
	static <K, V> TypeToken<MultiEntryValueHandle<K, V>> buildEntryValueType(TypeToken<K> keyType, TypeToken<V> valueType) {
		return VALUE_ENTRY_KEY.getCompoundType(keyType, valueType);
	}

	@Override
	abstract boolean isLockSupported();

	/** @return The keys that have least one value in this map */
	@Override
	ObservableSet<K> keySet();

	/**
	 * @param key The key to get values for
	 * @return The collection of values stored for the given key in this map. Never null.
	 */
	@Override
	ObservableCollection<V> get(Object key);

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
	default ObservableMap<K, V> singleMap(boolean firstValue) {
		return new ObservableSingleMap<>(this, firstValue);
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
	 * @param <K> The (compile-time) key type for the map
	 * @param <V> The (compile-time) value type for the map
	 * @param keyType The (run-time) key type for the map
	 * @param valueType The (run-time) value type for the map
	 * @param keyEquivalence The equivalence to use for the map's key set distinctness
	 * @return A flow that may be transformed, if desired, then {@link MultiMapFlow#gather() gathered} into an {@link ObservableMultiMap}
	 */
	static <K, V> MultiMapFlow<K, V> create(TypeToken<K> keyType, TypeToken<V> valueType, Equivalence<? super K> keyEquivalence) {
		return create(keyType, valueType, keyEquivalence, ObservableCollection.createDefaultBacking());
	}

	/**
	 * @param <K> The (compile-time) key type for the map
	 * @param <V> The (compile-time) value type for the map
	 * @param keyType The (run-time) key type for the map
	 * @param valueType The (run-time) value type for the map
	 * @param keyEquivalence The equivalence to use for the map's key set distinctness
	 * @param entryCollection The list to hold the map's entries. Should not be modified externally.
	 * @return A flow that may be transformed, if desired, then {@link MultiMapFlow#gather() gathered} into an {@link ObservableMultiMap}
	 */
	static <K, V> MultiMapFlow<K, V> create(TypeToken<K> keyType, TypeToken<V> valueType, Equivalence<? super K> keyEquivalence,
		BetterList<Map.Entry<K, V>> entryCollection) {
		TypeToken<Map.Entry<K, V>> entryType = new TypeToken<Map.Entry<K, V>>() {}.where(new TypeParameter<K>() {}, keyType.wrap())
			.where(new TypeParameter<V>() {}, valueType.wrap());
		ObservableCollection<Map.Entry<K, V>> simpleEntryCollection = ObservableCollection.create(entryType, entryCollection);
		if (keyEquivalence instanceof Equivalence.ComparatorEquivalence)
			return new ObservableMultiMapImpl.DefaultSortedMultiMapFlow<>(simpleEntryCollection.flow(),
				(Equivalence.ComparatorEquivalence<? super K>) keyEquivalence, Equivalence.DEFAULT, false);
		else
			return new ObservableMultiMapImpl.DefaultMultiMapFlow<>(simpleEntryCollection.flow(), keyEquivalence, Equivalence.DEFAULT,
				false);
	}

	/**
	 * A default toString implementation for {@link ObservableMultiMap} implementations
	 *
	 * @param map The map to print
	 * @return The string representation of the multi-map
	 */
	public static String toString(ObservableMultiMap<?, ?> map) {
		StringBuilder str = new StringBuilder();
		boolean first = true;
		for (MultiMap.MultiEntry<?, ?> entry : map.entrySet()) {
			if (!first)
				str.append('\n');
			first = false;
			str.append(entry.getKey()).append('=').append(entry.getValues());
		}
		return str.toString();
	}

	/**
	 * Implements {@link ObservableMultiMap#entrySet()}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ObservableMultiMapEntrySet<K, V> extends BetterMultiMapEntrySet<K, V> implements ObservableSet<MultiEntryHandle<K, V>> {
		private Equivalence<? super MultiMap.MultiEntry<? extends K, ?>> theEquivalence;

		public ObservableMultiMapEntrySet(BetterMultiMap<K, V> map) {
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

		/**
		 * @param <V2> The value type for the derived flow
		 * @param valueMap The function to produce a derived value flow from the value flow of each of this flow's entries' value
		 *        collections
		 * @return The derived flow
		 */
		<V2> MultiMapFlow<K, V2> withValues(Function<CollectionDataFlow<?, ?, V>, CollectionDataFlow<?, ?, V2>> valueMap);

		/** @return A flow that contains no 2 equivalent values in the entire map */
		default MultiMapFlow<K, V> distinctForMap() {
			return distinctForMap(options -> {});
		}

		/**
		 * @param options Options governing the value distinctness
		 * @return A flow that contains no 2 equivalent values in the entire map
		 */
		MultiMapFlow<K, V> distinctForMap(Consumer<UniqueOptions> options);

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
			return gather(options -> {});
		}

		/**
		 * @param options Options governing the multi-map's grouping behavior
		 * @return An ObservableMultiMap derived from this flow's source by this flow's configuration
		 */
		default ObservableMultiMap<K, V> gather(Consumer<GroupingOptions> options) {
			return gather(Observable.empty, options);
		}

		/**
		 * @param until The observable to terminate the active map's listening (to its source data)
		 * @return An ObservableMultiMap derived from this flow's source by this flow's configuration
		 */
		default ObservableMultiMap<K, V> gather(Observable<?> until) {
			return gather(until, options -> {});
		}

		/**
		 * @param until The observable to terminate the active map's listening (to its source data)
		 * @param options Options governing the multi-map's grouping behavior
		 * @return An ObservableMultiMap derived from this flow's source by this flow's configuration
		 */
		ObservableMultiMap<K, V> gather(Observable<?> until, Consumer<GroupingOptions> options);
	}
}
