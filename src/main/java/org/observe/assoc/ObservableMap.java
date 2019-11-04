package org.observe.assoc;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.CollectionSubscription;
import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.SubscriptionCause;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSet;
import org.observe.collect.SettableElement;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollection.EmptyCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableMapEntryHandle;
import org.qommons.collect.SimpleMapEntry;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A map with observable capabilities
 *
 * @param <K> The type of keys this map uses
 * @param <V> The type of values this map stores
 */
public interface ObservableMap<K, V> extends BetterMap<K, V> {
	/** This class's type key */
	@SuppressWarnings("rawtypes")
	static TypeTokens.TypeKey<ObservableMap> TYPE_KEY = TypeTokens.get().keyFor(ObservableMap.class)
	.enableCompoundTypes(new TypeTokens.BinaryCompoundTypeCreator<ObservableMap>() {
		@Override
		public <P1, P2> TypeToken<? extends ObservableMap> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2) {
			return new TypeToken<ObservableMap<P1, P2>>() {}.where(new TypeParameter<P1>() {}, param1).where(new TypeParameter<P2>() {},
				param2);
		}
	});
	/** This class's wildcard {@link TypeToken} */
	static TypeToken<ObservableMap<?, ?>> TYPE = TYPE_KEY.parameterized();

	/** This type key for {@link java.util.Map.Entry} */
	@SuppressWarnings("rawtypes")
	static TypeTokens.TypeKey<Map.Entry> ENTRY_KEY = TypeTokens.get().keyFor(Map.Entry.class)
	.enableCompoundTypes(new TypeTokens.BinaryCompoundTypeCreator<Map.Entry>() {
		@Override
		public <P1, P2> TypeToken<? extends Map.Entry> createCompoundType(TypeToken<P1> param1, TypeToken<P2> param2) {
			return new TypeToken<Map.Entry<P1, P2>>() {}.where(new TypeParameter<P1>() {}, param1).where(new TypeParameter<P2>() {},
				param2);
		}
	});
	/** This wildcard {@link TypeToken} for {@link java.util.Map.Entry} */
	static TypeToken<Map.Entry<?, ?>> ENTRY_TYPE = ENTRY_KEY.parameterized();

	/** @return The type of keys this map uses */
	TypeToken<K> getKeyType();

	/** @return The type of values this map stores */
	TypeToken<V> getValueType();

	/** @return The type of elements in the entry set. Should be cached, as type tokens aren't cheap to build. */
	TypeToken<Map.Entry<K, V>> getEntryType();

	/**
	 * Builds an {@link #getEntryType() entry type} from the key and value types
	 *
	 * @param keyType The key type of the map
	 * @param valueType The value type of the map
	 * @return The entry type for the map
	 */
	static <K, V> TypeToken<Map.Entry<K, V>> buildEntryType(TypeToken<K> keyType, TypeToken<V> valueType) {
		return ENTRY_KEY.getCompoundType(keyType, valueType);
	}

	@Override
	abstract boolean isLockSupported();

	/** @return The {@link Equivalence} that is used by this map's values (for {@link #containsValue(Object)}) */
	Equivalence<? super V> equivalence();

	@Override
	ObservableSet<K> keySet();

	/**
	 * @param action The action to perform whenever this map changes
	 * @return The subscription to cease listening
	 */
	Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action);

	/** @return An observable collection of all the values stored in this map */
	@Override
	default ObservableCollection<V> values() {
		return new ObservableMapValueCollection<>(this);
	}

	@Override
	default ObservableSet<Map.Entry<K, V>> entrySet() {
		return new ObservableEntrySet<>(this);
	}

	/**
	 * @param action The action to perform on map events
	 * @param forward Whether to subscribe to the map forward or reverse)
	 * @return The collection subscription to use to terminate listening
	 */
	default CollectionSubscription subscribe(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action, boolean forward) {
		try (Transaction t = lock(false, null)) {
			Subscription sub = onChange(action);
			SubscriptionCause subCause = new SubscriptionCause();
			try (Transaction ct = SubscriptionCause.use(subCause)) {
				int[] index = new int[] { forward ? 0 : size() - 1 };
				entrySet().spliterator(forward).forEachElement(entryEl -> {
					ObservableMultiMapEvent<K, V> mapEvent = new ObservableMultiMapEvent<>(entryEl.getElementId(), entryEl.getElementId(),
						getKeyType(), getValueType(), index[0], index[0], CollectionChangeType.add, entryEl.get().getKey(), null,
						entryEl.get().getValue(), subCause);
					try (Transaction mt = ObservableMultiMapEvent.use(mapEvent)) {
						action.accept(mapEvent);
					}
					if (forward)
						index[0]++;
					else
						index[0]--;
				}, forward);
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
						int[] index = new int[] { forward ? 0 : size() - 1 };
						entrySet().spliterator(forward).forEachElement(entryEl -> {
							ObservableMultiMapEvent<K, V> mapEvent = new ObservableMultiMapEvent<>(entryEl.getElementId(),
								entryEl.getElementId(), getKeyType(), getValueType(), index[0], index[0], CollectionChangeType.remove,
								entryEl.get().getKey(), entryEl.get().getValue(), entryEl.get().getValue(), unsubCause);
							try (Transaction mt = ObservableMultiMapEvent.use(mapEvent)) {
								action.accept(mapEvent);
							}
							if (!forward)
								index[0]--;
						}, forward);
					}
				}
			};
		}
	}

	/**
	 * @param key The key to get the value for
	 * @return An observable value that changes whenever the value for the given key changes in this map
	 */
	default <K2> SettableElement<V> observe(K2 key) {
		if (!keySet().belongs(key))
			return SettableElement.empty(getValueType());
		class MapValueObservable extends AbstractIdentifiable implements SettableElement<V> {
			private ElementId thePreviousElement;

			@Override
			public TypeToken<V> getType() {
				return getValueType();
			}

			@Override
			public ElementId getElementId() {
				if (thePreviousElement == null || !thePreviousElement.isPresent()) {
					MapEntryHandle<K, V> entry = getEntry((K) key);
					thePreviousElement = entry == null ? null : entry.getElementId();
				}
				return thePreviousElement;
			}

			@Override
			public V get() {
				try (Transaction t = ObservableMap.this.lock(false, null)) {
					MapEntryHandle<K, V> entry;
					if (thePreviousElement != null && thePreviousElement.isPresent())
						entry = getEntryById(thePreviousElement);
					else {
						entry = getEntry((K) key);
						thePreviousElement = entry == null ? null : entry.getElementId();
					}
					return entry == null ? null : entry.getValue();
				}
			}

			@Override
			public Observable<ObservableValueEvent<V>> noInitChanges() {
				class MapValueChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<V>> {
					@Override
					public Subscription subscribe(Observer<? super ObservableValueEvent<V>> observer) {
						try (Transaction t = lock()) {
							boolean[] exists = new boolean[1];
							Subscription sub = onChange(evt -> {
								if (!keySet().equivalence().elementEquals(evt.getKey(), key))
									return;
								boolean newExists;
								if (evt.getNewValue() != null)
									newExists = true;
								else
									newExists = keySet().contains(key);
								V oldValue = exists[0] ? evt.getOldValue() : null;
								V newValue = newExists ? evt.getNewValue() : null;
								exists[0] = newExists;
								ObservableValueEvent<V> evt2 = createChangeEvent(oldValue, newValue, evt);
								try (Transaction evtT = Causable.use(evt2)) {
									observer.onNext(evt2);
								}
							});
							CollectionElement<Map.Entry<K, V>> entryEl = entrySet().getElement(new SimpleMapEntry<>((K) key, null), true);
							exists[0] = entryEl != null;
							return sub;
						}
					}

					@Override
					public boolean isSafe() {
						return ObservableMap.this.isLockSupported();
					}

					@Override
					public Transaction lock() {
						return ObservableMap.this.lock(false, null);
					}

					@Override
					public Transaction tryLock() {
						return ObservableMap.this.tryLock(false, false, null);
					}

					@Override
					protected Object createIdentity() {
						return Identifiable.wrap(MapValueObservable.this.getIdentity(), "noInitChanges");
					}
				}
				return new MapValueChanges();
			}

			@Override
			public long getStamp() {
				return ObservableMap.this.getStamp(false);
			}

			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(ObservableMap.this.getIdentity(), "observeValue", key);
			}
		}
		return new MapValueObservable();
	}

	/**
	 * @return An observable that fires a (null) value whenever anything in this structure changes. This observable will only fire 1 event
	 *         per transaction.
	 */
	default Observable<Object> changes() {
		return values().simpleChanges();
	}

	/**
	 * Creates a builder to build an unconstrained {@link ObservableMap}
	 *
	 * @param keyType The key type for the map
	 * @param valueType The value type for the map
	 * @return The builder to build the map
	 */
	static <K, V> Builder<K, V> build(TypeToken<K> keyType, TypeToken<V> valueType) {
		return new Builder<>(keyType, valueType, "ObservableMap");
	}

	/**
	 * Builds an unconstrained {@link ObservableMap}
	 *
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 */
	class Builder<K, V> extends DefaultObservableCollection.Builder<K> {
		private final TypeToken<V> theValueType;

		Builder(TypeToken<K> keyType, TypeToken<V> valueType, String initDescrip) {
			super(keyType, initDescrip);
			theValueType = valueType;
		}

		@Override
		public Builder<K, V> withBacking(BetterList<K> backing) {
			super.withBacking(backing);
			return this;
		}

		@Override
		public Builder<K, V> withEquivalence(Equivalence<? super K> equivalence) {
			super.withEquivalence(equivalence);
			return this;
		}

		@Override
		public Builder<K, V> withLocker(CollectionLockingStrategy locker) {
			super.withLocker(locker);
			return this;
		}

		@Override
		public Builder<K, V> safe(boolean safe) {
			super.safe(safe);
			return this;
		}

		@Override
		public Builder<K, V> sortBy(Comparator<? super K> sorting) {
			super.sortBy(sorting);
			return this;
		}

		@Override
		public Builder<K, V> withDescription(String description) {
			super.withDescription(description);
			return this;
		}

		@Override
		public Builder<K, V> withElementSource(Function<ElementId, ElementId> elementSource) {
			super.withElementSource(elementSource);
			return this;
		}

		@Override
		public Builder<K, V> withSourceElements(BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> sourceElements) {
			super.withSourceElements(sourceElements);
			return this;
		}

		protected TypeToken<V> getValueType() {
			return theValueType;
		}

		public ObservableMap<K, V> buildMap() {
			Comparator<? super K> compare = getSorting();
			DefaultObservableCollection.Builder<Map.Entry<K, V>> entryBuilder = DefaultObservableCollection
				.build(buildEntryType(getType(), theValueType))//
				.withBacking((BetterList<Map.Entry<K, V>>) (BetterList<?>) getBacking())//
				.withDescription(getDescription())//
				.withElementSource(getElementSource()).withSourceElements(getSourceElements())//
				.withLocker(getLocker());
			if (compare != null)
				entryBuilder.sortBy((entry1, entry2) -> compare.compare(entry1.getKey(), entry2.getKey()));
			return new DefaultObservableMap<>(getType(), theValueType, getEquivalence(), entryBuilder.build());
		}
	}

	/**
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param keyType The key type for the map
	 * @param valueType The value type for the map
	 * @return An immutable, empty map with the given types
	 */
	static <K, V> ObservableMap<K, V> empty(TypeToken<K> keyType, TypeToken<V> valueType) {
		return new EmtpyObservableMap<>(keyType, valueType);
	}

	/**
	 * Implements {@link ObservableMap#values()}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ObservableMapValueCollection<K, V> extends BetterMap.BetterMapValueCollection<K, V> implements ObservableCollection<V> {
		public ObservableMapValueCollection(ObservableMap<K, V> map) {
			super(map);
		}

		@Override
		protected ObservableMap<K, V> getMap() {
			return (ObservableMap<K, V>) super.getMap();
		}

		@Override
		public boolean isContentControlled() {
			return true;
		}

		@Override
		public TypeToken<V> getType() {
			return getMap().getValueType();
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public CollectionElement<V> getElement(int index) throws IndexOutOfBoundsException {
			return getMap().getEntryById(getMap().keySet().getElement(index).getElementId());
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
		public void setValue(Collection<ElementId> elements, V value) {
			try (Transaction t = lock(true, false, null)) {
				for (ElementId entryId : elements) {
					MutableMapEntryHandle<K, V> entry = getMap().mutableEntry(entryId);
					entry.set(value);
				}
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends V>> observer) {
			return getMap().onChange(observer);
		}
	}

	/**
	 * Implements {@link ObservableMap#entrySet()}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class ObservableEntrySet<K, V> extends BetterMap.BetterEntrySet<K, V> implements ObservableSet<Map.Entry<K, V>> {
		private TypeToken<Map.Entry<K, V>> theType;
		private Equivalence<Map.Entry<K, V>> theEquivalence;

		public ObservableEntrySet(ObservableMap<K, V> map) {
			super(map);
		}

		@Override
		protected ObservableMap<K, V> getMap() {
			return (ObservableMap<K, V>) super.getMap();
		}

		@Override
		public TypeToken<Map.Entry<K, V>> getType() {
			if (theType == null)
				theType = buildEntryType(getMap().getKeyType(), getMap().getValueType());
			return theType;
		}

		@Override
		public boolean isContentControlled() {
			return getMap().keySet().isContentControlled();
		}

		@Override
		public Equivalence<? super Map.Entry<K, V>> equivalence() {
			if (theEquivalence == null)
				theEquivalence = getMap().keySet().equivalence().map((Class<Map.Entry<K, V>>) (Class<?>) Map.Entry.class, null, //
					key -> new SimpleMapEntry<>(key, null, false), Map.Entry::getKey);
			return theEquivalence;
		}

		@Override
		public CollectionElement<Entry<K, V>> getElement(int index) throws IndexOutOfBoundsException {
			return getElement(getMap().keySet().getElement(index).getElementId());
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
		public void setValue(Collection<ElementId> elements, Map.Entry<K, V> value) {
			try (Transaction t = lock(true, false, null)) {
				for (ElementId entryId : elements) {
					MutableMapEntryHandle<K, V> entry = getMap().mutableEntry(entryId);
					if (!getMap().keySet().equivalence().elementEquals(entry.getKey(), value.getKey()))
						throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
					entry.set(value.getValue());
				}
			}
		}

		@Override
		public Map.Entry<K, V>[] toArray() {
			try (Transaction t = lock(false, true, null)) {
				Map.Entry<K, V>[] array = new Map.Entry[size()];
				CollectionElement<K> keyElement = getMap().keySet().getTerminalElement(true);
				for (int i = 0; keyElement != null; i++, keyElement = getMap().keySet().getAdjacentElement(keyElement.getElementId(),
					true)) {
					array[i++] = getMap().getEntryById(keyElement.getElementId());
				}
				return array;
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableCollectionEvent<? extends Map.Entry<K, V>>> observer) {
			return getMap().onChange(mapEvt -> {
				MapEntryHandle<K, V> entry = getMap().getEntryById(mapEvt.getElementId());
				Map.Entry<K, V> oldEntry;
				if (mapEvt.getOldValue() == mapEvt.getNewValue())
					oldEntry = entry;
				else
					oldEntry = new SimpleMapEntry<>(mapEvt.getKey(), mapEvt.getOldValue(), false);
				ObservableCollectionEvent<Map.Entry<K, V>> entryEvt = new ObservableCollectionEvent<>(//
					mapEvt.getElementId(), getType(), mapEvt.getIndex(), mapEvt.getType(), oldEntry, entry, mapEvt);
				try (Transaction evtT = Causable.use(entryEvt)) {
					observer.accept(entryEvt);
				}
			});
		}
	}

	/**
	 * A simple, unconstrained {@link ObservableMap} implementation
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class DefaultObservableMap<K, V> extends AbstractIdentifiable implements ObservableMap<K, V> {
		private final TypeToken<V> theValueType;
		private final ObservableSet<Map.Entry<K, V>> theEntries;
		private final ObservableSet<K> theKeySet;
		private ObservableCollection<V> theValues;
		private ObservableSet<Map.Entry<K, V>> theExposedEntries;

		public DefaultObservableMap(TypeToken<K> keyType, TypeToken<V> valueType, Equivalence<? super K> keyEquivalence,
			ObservableCollection<Map.Entry<K, V>> entries) {
			theValueType = valueType;
			if (keyEquivalence instanceof Equivalence.ComparatorEquivalence) {
				Comparator<? super K> compare = ((Equivalence.ComparatorEquivalence<? super K>) keyEquivalence).comparator();
				theEntries = entries.flow().distinctSorted((entry1, entry2) -> compare.compare(entry1.getKey(), entry2.getKey()), true)
					.collect();
			} else
				theEntries = entries.flow().distinct().collect();
			theKeySet = theEntries.flow()
				.mapEquivalent(keyType, Map.Entry::getKey, key -> new SimpleMapEntry<>(key, null, false),
					opts -> opts.withEquivalence(keyEquivalence).cache(false).reEvalOnUpdate(false).fireIfUnchanged(false))
				.collectPassive();
		}

		@Override
		public Object createIdentity() {
			return Identifiable.baseId("observable-map", this);
		}

		@Override
		public boolean isLockSupported() {
			return theEntries.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theEntries.lock(write, structural, cause);
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theKeySet.getType();
		}

		@Override
		public TypeToken<V> getValueType() {
			return theValueType;
		}

		@Override
		public TypeToken<Map.Entry<K, V>> getEntryType() {
			return theEntries.getType();
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public ObservableSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public ObservableCollection<V> values() {
			if (theValues == null)
				theValues = ObservableMap.super.values();
			return theValues;
		}

		@Override
		public ObservableSet<Map.Entry<K, V>> entrySet() {
			if (theExposedEntries == null)
				theExposedEntries = ObservableMap.super.entrySet();
			return theExposedEntries;
		}

		@Override
		public MapEntryHandle<K, V> getEntry(K key) {
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getElement(new SimpleMapEntry<>(key, null), true);
			return entryEl == null ? null : handleFor(entryEl);
		}

		@Override
		public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getElement(entryId);
			return entryEl == null ? null : handleFor(entryEl);
		}

		@Override
		public MapEntryHandle<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
			try (Transaction t = lock(true, true, null)) {
				CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getElement(new SimpleMapEntry<>(key, null), true);
				if (entryEl != null) {
					entryEl.get().setValue(value);
					return handleFor(entryEl);
				}
				MapEntry newEntry = new MapEntry(key, value);
				entryEl = theEntries.addElement(newEntry, after, before, first);
				newEntry.theElementId = entryEl.getElementId();
				return handleFor(entryEl);
			}
		}

		@Override
		public MapEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId afterKey, ElementId beforeKey,
			boolean first, Runnable added) {
			CollectionElement<Map.Entry<K, V>> entryEl = theEntries.getOrAdd(new SimpleMapEntry<>(key, value.apply(key)), afterKey,
				beforeKey, first, added);
			return entryEl == null ? null : handleFor(entryEl);
		}

		@Override
		public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
			MutableCollectionElement<Map.Entry<K, V>> entryEl = theEntries.mutableElement(entryId);
			return entryEl == null ? null : mutableHandleFor(entryEl);
		}

		private MapEntryHandle<K, V> handleFor(CollectionElement<Map.Entry<K, V>> entryEl) {
			return new MapEntryHandle<K, V>() {
				@Override
				public ElementId getElementId() {
					return entryEl.getElementId();
				}

				@Override
				public V get() {
					return entryEl.get().getValue();
				}

				@Override
				public K getKey() {
					return entryEl.get().getKey();
				}
			};
		}

		private MutableMapEntryHandle<K, V> mutableHandleFor(MutableCollectionElement<Map.Entry<K, V>> entryEl) {
			return new MutableMapEntryHandle<K, V>() {
				@Override
				public K getKey() {
					return entryEl.get().getKey();
				}

				@Override
				public BetterCollection<V> getCollection() {
					return values();
				}

				@Override
				public ElementId getElementId() {
					return entryEl.getElementId();
				}

				@Override
				public V get() {
					return entryEl.get().getValue();
				}

				@Override
				public String isEnabled() {
					return null;
				}

				@Override
				public String isAcceptable(V value) {
					if (value != null && !TypeTokens.get().isInstance(theValueType, value))
						return StdMsg.BAD_TYPE;
					return null;
				}

				@Override
				public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
					if (value != null && !TypeTokens.get().isInstance(theValueType, value))
						throw new IllegalArgumentException(StdMsg.BAD_TYPE);
					entryEl.get().setValue(value);
				}

				@Override
				public String canRemove() {
					return entryEl.canRemove();
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					entryEl.remove();
				}

				@Override
				public String canAdd(V value, boolean before) {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public ElementId add(V value, boolean before) throws UnsupportedOperationException, IllegalArgumentException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}
			};
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			return theEntries.onChange(evt -> {
				V oldValue = ((MapEntry) evt.getNewValue()).getOldValue();
				ObservableMapEvent<K, V> mapEvent = new ObservableMapEvent<>(evt.getElementId(), getKeyType(), theValueType, evt.getIndex(),
					evt.getType(), evt.getNewValue().getKey(), oldValue, evt.getNewValue().getValue(), evt);
				try (Transaction t = Causable.use(mapEvent)) {
					action.accept(mapEvent);
				}
			});
		}

		@Override
		public int hashCode() {
			return BetterMap.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterMap.equals(this, obj);
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}

		class MapEntry implements Map.Entry<K, V> {
			private final K theKey;
			private ElementId theElementId;
			private V theOldValue;
			private V theValue;

			MapEntry(K key, V value) {
				theKey = key;
				theValue = value;
			}

			@Override
			public K getKey() {
				return theKey;
			}

			@Override
			public V getValue() {
				return theValue;
			}

			V getOldValue() {
				return theOldValue;
			}

			@Override
			public V setValue(V value) {
				try (Transaction t = theEntries.lock(true, false, null)) {
					V oldValue;
					synchronized (this) {
						oldValue = theValue;
						theOldValue = oldValue;
						theValue = value;
						theEntries.mutableElement(theElementId).set(this);
					}
					return oldValue;
				}
			}
		}
	}

	/**
	 * An empty {@link ObservableMap} implementation
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class EmtpyObservableMap<K, V> extends AbstractIdentifiable implements ObservableMap<K, V> {
		private final TypeToken<K> theKeyType;
		private final TypeToken<V> theValueType;
		private TypeToken<Map.Entry<K, V>> theEntryType;
		private final ObservableSet<K> theKeySet;
		private ObservableCollection<V> theValues;
		private ObservableSet<Map.Entry<K, V>> theEntries;

		public EmtpyObservableMap(TypeToken<K> keyType, TypeToken<V> valueType) {
			theKeyType = keyType;
			theValueType = valueType;
			theKeySet = ObservableCollection.of(keyType).flow().distinct().collect();
		}

		@Override
		public Object createIdentity() {
			return Identifiable.idFor(this, this::toString, this::hashCode, other -> other instanceof EmptyCollection);
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theKeyType;
		}

		@Override
		public TypeToken<V> getValueType() {
			return theValueType;
		}

		@Override
		public TypeToken<Map.Entry<K, V>> getEntryType() {
			if (theEntryType == null)
				theEntryType = buildEntryType(theKeyType, theValueType);
			return theEntryType;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public ObservableSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public ObservableCollection<V> values() {
			if (theValues == null)
				theValues = ObservableMap.super.values();
			return theValues;
		}

		@Override
		public ObservableSet<Entry<K, V>> entrySet() {
			if (theEntries == null)
				theEntries = ObservableMap.super.entrySet();
			return theEntries;
		}

		@Override
		public MapEntryHandle<K, V> putEntry(K key, V value, ElementId after, ElementId before, boolean first) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public MapEntryHandle<K, V> getEntry(K key) {
			return null;
		}

		@Override
		public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
			throw new NoSuchElementException();
		}

		@Override
		public MapEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable added) {
			return null;
		}

		@Override
		public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
			throw new NoSuchElementException();
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			return Subscription.NONE;
		}

		@Override
		public int hashCode() {
			return BetterMap.hashCode(this);
		}

		@Override
		public boolean equals(Object obj) {
			return BetterMap.equals(this, obj);
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}
}
