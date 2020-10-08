package org.observe.assoc;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.CollectionSubscription;
import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSet;
import org.observe.collect.SettableElement;
import org.observe.util.ObservableUtils.SubscriptionCause;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollection.EmptyCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableMapEntryHandle;
import org.qommons.collect.SimpleMapEntry;

import com.google.common.reflect.TypeToken;

/**
 * A map with observable capabilities
 *
 * @param <K> The type of keys this map uses
 * @param <V> The type of values this map stores
 */
public interface ObservableMap<K, V> extends BetterMap<K, V> {
	/** This class's wildcard {@link TypeToken} */
	static TypeToken<ObservableMap<?, ?>> TYPE = TypeTokens.get().keyFor(ObservableMap.class).wildCard();

	/** The wildcard {@link java.util.Map.Entry Map.Entry} {@link TypeToken} */
	static TypeToken<Map.Entry<?, ?>> ENTRY_TYPE = TypeTokens.get().keyFor(Map.Entry.class).wildCard();

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
		return TypeTokens.get().keyFor(Map.Entry.class).parameterized(keyType, valueType);
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
			try (Transaction ct = subCause.use()) {
				int index = forward ? 0 : size() - 1;
				for (CollectionElement<Map.Entry<K, V>> entryEl : entrySet().elements()) {
					ObservableMultiMapEvent<K, V> mapEvent = new ObservableMultiMapEvent<>(entryEl.getElementId(), entryEl.getElementId(),
						getKeyType(), getValueType(), index, index, CollectionChangeType.add, entryEl.get().getKey(), null,
						entryEl.get().getValue(), subCause);
					try (Transaction mt = mapEvent.use()) {
						action.accept(mapEvent);
					}
					if (forward)
						index++;
					else
						index--;
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
					try (Transaction ct = unsubCause.use()) {
						int index = !forward ? 0 : size() - 1;
						for (CollectionElement<Map.Entry<K, V>> entryEl : entrySet().elements()) {
							ObservableMultiMapEvent<K, V> mapEvent = new ObservableMultiMapEvent<>(entryEl.getElementId(),
								entryEl.getElementId(), getKeyType(), getValueType(), index, index, CollectionChangeType.remove,
								entryEl.get().getKey(), entryEl.get().getValue(), entryEl.get().getValue(), subCause);
							try (Transaction mt = mapEvent.use()) {
								action.accept(mapEvent);
							}
							if (forward)
								index--;
						}
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
			public boolean isLockSupported() {
				return ObservableMap.this.isLockSupported();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return ObservableMap.this.lock(write, cause);
			}

			@Override
			public Transaction tryLock(boolean write, Object cause) {
				return ObservableMap.this.tryLock(write, cause);
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
			public Observable<ObservableElementEvent<V>> elementChangesNoInit() {
				class MapValueChanges extends AbstractIdentifiable implements Observable<ObservableElementEvent<V>> {
					@Override
					public Subscription subscribe(Observer<? super ObservableElementEvent<V>> observer) {
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
								ObservableElementEvent<V> evt2 = new ObservableElementEvent<>(getType(), false, //
									evt.getType() == CollectionChangeType.add ? null : evt.getElementId(), evt.getElementId(), //
										oldValue, newValue, evt);
								if (evt.getType() == CollectionChangeType.remove)
									thePreviousElement = null;
								else
									thePreviousElement = evt.getElementId();
								try (Transaction evtT = evt2.use()) {
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
						return ObservableMap.this.tryLock(false, null);
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
				return ObservableMap.this.getStamp();
			}

			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(ObservableMap.this.getIdentity(), "observeValue", key);
			}

			@Override
			public ObservableValue<String> isEnabled() {
				class Enabled extends AbstractIdentifiable implements ObservableValue<String> {
					@Override
					public long getStamp() {
						return ObservableMap.this.getStamp();
					}

					@Override
					public Object createIdentity() {
						return Identifiable.wrap(MapValueObservable.this.getIdentity(), "enabled");
					}

					@Override
					public TypeToken<String> getType() {
						return TypeTokens.get().STRING;
					}

					@Override
					public String get() {
						try (Transaction t = ObservableMap.this.lock(false, null)) {
							MapEntryHandle<K, V> entry;
							if (thePreviousElement != null && thePreviousElement.isPresent())
								entry = getEntryById(thePreviousElement);
							else {
								entry = getEntry((K) key);
								thePreviousElement = CollectionElement.getElementId(entry);
							}
							if (entry != null)
								return mutableEntry(entry.getElementId()).isEnabled();
							else
								return keySet().canAdd((K) key, null, null);
						}
					}

					@Override
					public Observable<ObservableValueEvent<String>> noInitChanges() {
						class NoInitChanges extends AbstractIdentifiable implements Observable<ObservableValueEvent<String>> {
							@Override
							public Subscription subscribe(Observer<? super ObservableValueEvent<String>> observer) {
								String[] oldValue = new String[] { get() };
								return ObservableMap.this.onChange(mapEvt -> {
									if (keySet().equivalence().elementEquals(mapEvt.getKey(), key)) {
										String newValue = get();
										if (!Objects.equals(newValue, oldValue[0])) {
											ObservableValueEvent<String> enabledEvt = createChangeEvent(oldValue[0], newValue, mapEvt);
											try (Transaction evtT = enabledEvt.use()) {
												observer.onNext(enabledEvt);
											}
										}
									}
								});
							}

							@Override
							public boolean isSafe() {
								return Enabled.this.isLockSupported();
							}

							@Override
							public Transaction lock() {
								return ObservableMap.this.lock(false, null);
							}

							@Override
							public Transaction tryLock() {
								return ObservableMap.this.tryLock(false, null);
							}

							@Override
							protected Object createIdentity() {
								return Identifiable.wrap(Enabled.this.getIdentity(), "noInitChanges");
							}
						}
						return new NoInitChanges();
					}
				}
				return new Enabled();
			}

			@Override
			public <V2 extends V> String isAcceptable(V2 value) {
				try (Transaction t = ObservableMap.this.lock(false, null)) {
					MapEntryHandle<K, V> entry;
					if (thePreviousElement != null && thePreviousElement.isPresent())
						entry = getEntryById(thePreviousElement);
					else {
						entry = getEntry((K) key);
						thePreviousElement = CollectionElement.getElementId(entry);
					}
					if (entry != null)
						return mutableEntry(entry.getElementId()).isAcceptable(value);
					else
						return keySet().canAdd((K) key, null, null);
				}
			}

			@Override
			public <V2 extends V> V set(V2 value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
				try (Transaction t = ObservableMap.this.lock(false, cause)) {
					MapEntryHandle<K, V> entry;
					if (thePreviousElement != null && thePreviousElement.isPresent())
						entry = getEntryById(thePreviousElement);
					else {
						entry = getEntry((K) key);
						thePreviousElement = CollectionElement.getElementId(entry);
					}
					if (entry != null) {
						V oldValue = entry.getValue();
						mutableEntry(entry.getElementId()).set(value);
						return oldValue;
					} else {
						mutableEntry(keySet().addElement((K) key, null, null, false).getElementId()).set(value);
						return null;
					}
				}
			}

			@Override
			public Observable<ObservableElementEvent<V>> elementChanges() {
				class MapObservableWithInit extends AbstractIdentifiable implements Observable<ObservableElementEvent<V>> {
					private final Observable<ObservableElementEvent<V>> changes = elementChangesNoInit();

					@Override
					public Object createIdentity() {
						return Identifiable.wrap(MapValueObservable.this.getIdentity(), "changes");
					}

					@Override
					public Subscription subscribe(Observer<? super ObservableElementEvent<V>> observer) {
						try (Transaction t = ObservableMap.this.lock(false, null)) {
							MapEntryHandle<K, V> entry;
							if (thePreviousElement != null && thePreviousElement.isPresent())
								entry = getEntryById(thePreviousElement);
							else {
								entry = getEntry((K) key);
								thePreviousElement = CollectionElement.getElementId(entry);
							}
							ObservableElementEvent<V> initEvt = new ObservableElementEvent<>(getType(), true, null,
								entry == null ? null : entry.getElementId(), //
									null, entry == null ? null : entry.getValue(), null);
							try (Transaction evtT = initEvt.use()) {
								observer.onNext(initEvt);
							}
							return changes.subscribe(observer);
						}
					}

					@Override
					public boolean isSafe() {
						return changes.isSafe();
					}

					@Override
					public Transaction lock() {
						return changes.lock();
					}

					@Override
					public Transaction tryLock() {
						return changes.tryLock();
					}
				}
				return new MapObservableWithInit();
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
	static <K, V> Builder<K, V, ?> build(TypeToken<K> keyType, TypeToken<V> valueType) {
		return new Builder<>(keyType, valueType, "ObservableMap");
	}

	/**
	 * Builds an unconstrained {@link ObservableMap}
	 *
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param <B> The sub-type of the builder
	 */
	class Builder<K, V, B extends Builder<K, V, B>> extends ObservableCollectionBuilder.CollectionBuilderImpl<K, B> {
		private final TypeToken<V> theValueType;

		Builder(TypeToken<K> keyType, TypeToken<V> valueType, String initDescrip) {
			super(keyType, initDescrip);
			theValueType = valueType;
		}

		protected TypeToken<V> getValueType() {
			return theValueType;
		}

		public ObservableMap<K, V> buildMap() {
			Comparator<? super K> compare = getSorting();
			ObservableCollectionBuilder<Map.Entry<K, V>, ?> entryBuilder = DefaultObservableCollection
				.build(buildEntryType(getType(), theValueType))//
				.withBacking((BetterList<Map.Entry<K, V>>) (BetterList<?>) getBacking())//
				.withDescription(getDescription())//
				.withElementSource(getElementSource()).withSourceElements(getSourceElements())//
				.withLocker(this::getLocker);
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
		return new EmptyObservableMap<>(keyType, valueType);
	}

	/**
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to wrap
	 * @return An {@link ObservableMap} that reflects the given map's contents but does not allow any modifications
	 */
	static <K, V> ObservableMap<K, V> unmodifiable(ObservableMap<K, V> map) {
		return new UnmodifiableObservableMap<>(map);
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
			try (Transaction t = lock(true, null)) {
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
			try (Transaction t = lock(true, null)) {
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
			try (Transaction t = lock(false, null)) {
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
				try (Transaction evtT = entryEvt.use()) {
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
			if (keyEquivalence instanceof Equivalence.SortedEquivalence) {
				Comparator<? super K> compare = ((Equivalence.SortedEquivalence<? super K>) keyEquivalence).comparator();
				theEntries = entries.flow().distinctSorted((entry1, entry2) -> compare.compare(entry1.getKey(), entry2.getKey()), true)
					.collect();
			} else
				theEntries = entries.flow().distinct().collect();
			theKeySet = theEntries.flow()
				.transformEquivalent(keyType,
					tx -> tx.cache(false).reEvalOnUpdate(false).fireIfUnchanged(false)//
					.map(Map.Entry::getKey).withEquivalence(keyEquivalence).withReverse(key -> new SimpleMapEntry<>(key, null, false)))
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
		public Transaction lock(boolean write, Object cause) {
			return theEntries.lock(write, cause);
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
			try (Transaction t = lock(true, null)) {
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
				try (Transaction t = mapEvent.use()) {
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
				try (Transaction t = theEntries.lock(true, null)) {
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
	class EmptyObservableMap<K, V> extends AbstractIdentifiable implements ObservableMap<K, V> {
		private final TypeToken<K> theKeyType;
		private final TypeToken<V> theValueType;
		private TypeToken<Map.Entry<K, V>> theEntryType;
		private final ObservableSet<K> theKeySet;
		private ObservableCollection<V> theValues;
		private ObservableSet<Map.Entry<K, V>> theEntries;

		public EmptyObservableMap(TypeToken<K> keyType, TypeToken<V> valueType) {
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
		public Transaction lock(boolean write, Object cause) {
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

	/**
	 * Implements {@link ObservableMap#unmodifiable(ObservableMap)}
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 */
	class UnmodifiableObservableMap<K, V> implements ObservableMap<K, V> {
		private final ObservableMap<K, V> theWrapped;
		private final ObservableSet<K> theKeySet;

		public UnmodifiableObservableMap(ObservableMap<K, V> wrapped) {
			theWrapped = wrapped;
			theKeySet = theWrapped.keySet().flow().unmodifiable().collectPassive();
		}

		@Override
		public MapEntryHandle<K, V> getEntry(K key) {
			return theWrapped.getEntry(key);
		}

		@Override
		public MapEntryHandle<K, V> getOrPutEntry(K key, Function<? super K, ? extends V> value, ElementId after, ElementId before,
			boolean first, Runnable added) {
			MapEntryHandle<K, V> found = theWrapped.getEntry(key);
			if (found == null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			return found;
		}

		@Override
		public MapEntryHandle<K, V> getEntryById(ElementId entryId) {
			return theWrapped.getEntryById(entryId);
		}

		@Override
		public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
			return new UnmodifiableEntry(getEntryById(entryId));
		}

		@Override
		public Object getIdentity() {
			return theWrapped.getIdentity();
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theWrapped.getKeyType();
		}

		@Override
		public TypeToken<V> getValueType() {
			return theWrapped.getValueType();
		}

		@Override
		public TypeToken<Map.Entry<K, V>> getEntryType() {
			return theWrapped.getEntryType();
		}

		@Override
		public boolean isLockSupported() {
			return theWrapped.isLockSupported();
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return theWrapped.equivalence();
		}

		@Override
		public ObservableSet<K> keySet() {
			return theKeySet;
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			return theWrapped.onChange(action);
		}

		@Override
		public int hashCode() {
			return theWrapped.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof UnmodifiableObservableMap)
				obj = ((UnmodifiableObservableMap<?, ?>) obj).theWrapped;
			return theWrapped.equals(obj);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}

		class UnmodifiableEntry implements MutableMapEntryHandle<K, V> {
			private final MapEntryHandle<K, V> theWrappedEl;

			UnmodifiableEntry(MapEntryHandle<K, V> wrappedEl) {
				theWrappedEl = wrappedEl;
			}

			@Override
			public K getKey() {
				return theWrappedEl.getKey();
			}

			@Override
			public ElementId getElementId() {
				return theWrappedEl.getElementId();
			}

			@Override
			public V get() {
				return theWrappedEl.get();
			}

			@Override
			public BetterCollection<V> getCollection() {
				return UnmodifiableObservableMap.this.values();
			}

			@Override
			public String isEnabled() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public String isAcceptable(V value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public String canRemove() {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public void remove() throws UnsupportedOperationException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}
		}
	}
}
