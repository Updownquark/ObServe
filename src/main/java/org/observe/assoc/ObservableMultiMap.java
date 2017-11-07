package org.observe.assoc;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.CollectionSubscription;
import org.observe.collect.Equivalence;
import org.observe.collect.FlowOptions.GroupingOptions;
import org.observe.collect.FlowOptions.UniqueOptions;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.SubscriptionCause;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableSet;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMultiMap;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MultiEntryHandle;
import org.qommons.collect.MultiMap;
import org.qommons.collect.MultiMapEntryHandle;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.MutableElementSpliterator;
import org.qommons.collect.MutableMapEntryHandle;
import org.qommons.collect.SimpleCause;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * An observable map structure that allows more than one value to be stored per key
 *
 * @param <K> The type of key used by this map
 * @param <V> The type of values stored in this map
 */
public interface ObservableMultiMap<K, V> extends BetterMultiMap<K, V> {
	/**
	 * A {@link java.util.Map.Entry} with observable capabilities
	 *
	 * @param <K> The type of key this entry uses
	 * @param <V> The type of value this entry stores
	 */
	public interface ObservableMultiEntry<K, V> extends MultiEntryHandle<K, V> {
		/** @return The key type of this entry's map */
		TypeToken<K> getKeyType();

		@Override
		ObservableCollection<V> getValues();

		/**
		 * @param <K> The key type for the entry
		 * @param <V> The value type for the entry
		 * @param keyType The key type for the entry
		 * @param key The key for the entry
		 * @param keyEquivalence The key equivalence for the entry
		 * @param values The values for the entry
		 * @return The new entry
		 */
		static <K, V> ObservableMultiEntry<K, V> create(TypeToken<K> keyType, ElementId keyId, K key, Equivalence<? super K> keyEquivalence,
			ObservableCollection<V> values) {
			return new ObservableMultiEntry<K, V>() {
				@Override
				public TypeToken<K> getKeyType() {
					return keyType;
				}

				@Override
				public ElementId getElementId() {
					return keyId;
				}

				@Override
				public K getKey() {
					return key;
				}

				@Override
				public ObservableCollection<V> getValues() {
					return values;
				}

				@Override
				public int hashCode() {
					return Objects.hashCode(key);
				}

				@Override
				public boolean equals(Object obj) {
					if (!(obj instanceof ObservableMultiEntry))
						return false;
					ObservableMultiEntry<?, ?> other = (ObservableMultiEntry<?, ?>) obj;
					if (!keyEquivalence.isElement(other.getKey()))
						return false;
					return keyEquivalence.elementEquals(key, other.getKey());
				}

				@Override
				public String toString() {
					return key + "=" + values;
				}
			};
		}

		@Override
		default ObservableMultiEntry<K, V> reverse() {
			return new ReversedObservableMultiEntry<>(this);
		}

		/**
		 * A reversed observable multi-map entry
		 *
		 * @param <K> The key type of the multi-map
		 * @param <V> The value type of the multi-map
		 */
		class ReversedObservableMultiEntry<K, V> extends ReversedMultiEntryHandle<K, V> implements ObservableMultiEntry<K, V> {
			public ReversedObservableMultiEntry(ObservableMultiEntry<K, V> wrapped) {
				super(wrapped);
			}

			@Override
			protected ObservableMultiEntry<K, V> getSource() {
				return (ObservableMultiEntry<K, V>) super.getSource();
			}

			@Override
			public TypeToken<K> getKeyType() {
				return getSource().getKeyType();
			}

			@Override
			public ObservableCollection<V> getValues() {
				return (ObservableCollection<V>) super.getValues();
			}

			@Override
			public ObservableMultiEntry<K, V> reverse() {
				return getSource();
			}
		}
	}

	/** @return The type of keys this map uses */
	TypeToken<K> getKeyType();

	/** @return The type of values this map stores */
	TypeToken<V> getValueType();

	/** @return The type of elements in the entry set. Should be cached, as type tokens aren't cheap to build. */
	TypeToken<ObservableMultiEntry<K, V>> getEntryType();

	/**
	 * Builds an {@link #getEntryType() entry type} from the key and value types
	 *
	 * @param keyType The key type of the map
	 * @param valueType The value type of the map
	 * @return The entry type for the map
	 */
	static <K, V> TypeToken<ObservableMultiEntry<K, V>> buildEntryType(TypeToken<K> keyType, TypeToken<V> valueType) {
		return new TypeToken<ObservableMultiEntry<K, V>>() {}//
		.where(new TypeParameter<K>() {}, keyType.wrap())//
		.where(new TypeParameter<V>() {}, valueType.wrap());
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

	@Override
	ObservableMultiEntry<K, V> getEntry(ElementId keyId);

	@Override
	default ObservableMultiEntry<K, V> getEntry(K key) {
		return (ObservableMultiEntry<K, V>) BetterMultiMap.super.getEntry(key);
	}

	/**
	 * @param action The action to perform on changes to this map
	 * @return The collection subscription to terminate listening
	 */
	Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action);

	/**
	 * @param action The action to perform on initial map values and changes
	 * @param keyForward Whether to subscribe to the key set in forward or reverse order
	 * @param valueForward Whether to subscribe to the value collections for each key in forward or reverse order
	 * @return The collection subscription to terminate listening
	 */
	default CollectionSubscription subscribe(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action, boolean keyForward,
		boolean valueForward) {
		try (Transaction t = lock(false, null)) {
			Subscription sub = onChange(action);
			SubscriptionCause subCause = new SubscriptionCause();
			try (Transaction ct = SubscriptionCause.use(subCause)) {
				int[] keyIndex = new int[] { keyForward ? 0 : keySet().size() - 1 };
				entrySet().spliterator(keyForward).forEachElement(entryEl -> {
					ObservableCollection<V> values = entryEl.get().getValues();
					int[] valueIndex = new int[] { valueForward ? 0 : values.size() - 1 };
					values.spliterator(valueForward).forEachElement(valueEl -> {
						ObservableMapEvent<K, V> mapEvent = new ObservableMapEvent<>(entryEl.getElementId(), valueEl.getElementId(),
							getKeyType(), getValueType(), keyIndex[0], valueIndex[0], CollectionChangeType.add, entryEl.get().getKey(),
							null, valueEl.get(), subCause);
						try (Transaction mt = ObservableMapEvent.use(mapEvent)) {
							action.accept(mapEvent);
						}
						if (valueForward)
							valueIndex[0]++;
						else
							valueIndex[0]--;
					}, valueForward);
					if (keyForward)
						keyIndex[0]++;
					else
						keyIndex[0]--;
				}, keyForward);
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
						int[] keyIndex = new int[] { keyForward ? 0 : keySet().size() - 1 };
						entrySet().spliterator(keyForward).forEachElement(entryEl -> {
							ObservableCollection<V> values = entryEl.get().getValues();
							int[] valueIndex = new int[] { valueForward ? 0 : values.size() - 1 };
							values.spliterator(valueForward).forEachElement(valueEl -> {
								ObservableMapEvent<K, V> mapEvent = new ObservableMapEvent<>(entryEl.getElementId(), valueEl.getElementId(),
									getKeyType(), getValueType(), keyIndex[0], valueIndex[0], CollectionChangeType.remove,
									entryEl.get().getKey(), null, valueEl.get(), unsubCause);
								try (Transaction mt = ObservableMapEvent.use(mapEvent)) {
									action.accept(mapEvent);
								}
								if (!valueForward)
									valueIndex[0]--;
							}, valueForward);
							if (!keyForward)
								keyIndex[0]--;
						}, keyForward);
					}
				}
			};
		}
	}

	/**
	 * @return An observable collection of {@link ObservableMultiEntry observable entries} of all the key-value set pairs stored in this map
	 */
	@Override
	default ObservableSet<? extends ObservableMultiEntry<K, V>> entrySet() {
		return keySet().flow().mapEquivalent(getEntryType(), this::getEntry, entry -> entry.getKey(), options -> //
		options.cache(false)).collect();
	}

	/**
	 * @param key The key to store the value by
	 * @param value The value to store
	 * @return Whether the map was changed as a result
	 */
	@Override
	default boolean add(K key, V value) {
		return get(key).add(value);
	}

	/**
	 * @param key The key to store the value by
	 * @param values The values to store
	 * @return Whether the map was changed as a result
	 */
	@Override
	default boolean addAll(K key, Collection<? extends V> values) {
		return get(key).addAll(values);
	}

	/**
	 * @param key The key that the value may be stored by
	 * @param value The value to remove
	 * @return Whether the map was changed as a result
	 */
	@Override
	default boolean remove(K key, Object value) {
		return get(key).remove(value);
	}

	/**
	 * @param key The key to remove all values from
	 * @return Whether the map was changed as a result
	 */
	@Override
	default boolean removeAll(K key) {
		ObservableCollection<V> values = get(key);
		boolean ret = !values.isEmpty();
		values.clear();
		return ret;
	}

	/** @return A collection of plain (non-observable) {@link java.util.Map.Entry entries}, one for each value in this map */
	default ObservableCollection<Map.Entry<K, V>> observeSingleEntries() {
		class DefaultMapEntry implements Map.Entry<K, V> {
			private final K theKey;

			private final V theValue;

			DefaultMapEntry(K key, V value) {
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

			@Override
			public V setValue(V value) {
				throw new UnsupportedOperationException();
			}

			@Override
			public int hashCode() {
				return Objects.hashCode(theKey);
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof Map.Entry && Objects.equals(((Map.Entry<?, ?>) obj).getKey(), theKey);
			}

			@Override
			public String toString() {
				return theKey + "=" + theValue;
			}
		}
		TypeToken<Map.Entry<K, V>> entryType = new TypeToken<Map.Entry<K, V>>() {}//
		.where(new TypeParameter<K>() {}, getKeyType()).where(new TypeParameter<V>() {}, getValueType());

		return entrySet().flow()
			.flatMap(entryType, //
				entry -> entry.getValues().flow().map(entryType, value -> new DefaultMapEntry(entry.getKey(), value),
					options -> options.cache(false))//
				).collect();
	}

	default ObservableMap<K, V> single(BiFunction<K, ObservableCollection<V>, ObservableValue<V>> value) {
		return new SingleMap<>(this, value);
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

	static <K, V> MultiMapFlow<K, V> create(TypeToken<K> keyType, TypeToken<V> valueType, Equivalence<? super K> keyEquivalence) {
		return create(keyType, valueType, keyEquivalence, ObservableCollection.createDefaultBacking());
	}

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
	 * Implements {@link ObservableMultiMap#single(Function)}
	 *
	 * @param <K> The key-type of the map
	 * @param <V> The value-type of the map
	 */
	class SingleMap<K, V> implements ObservableMap<K, V> {
		private final ObservableMultiMap<K, V> theSource;
		private final BiFunction<K, ObservableCollection<V>, ObservableValue<V>> theValueMap;
		private final TypeToken<Map.Entry<K, V>> theEntryType;

		public SingleMap(ObservableMultiMap<K, V> outer, BiFunction<K, ObservableCollection<V>, ObservableValue<V>> valueMap) {
			theSource = outer;
			theValueMap = valueMap;
			theEntryType = ObservableMap.buildEntryType(getKeyType(), getValueType());
		}

		protected ObservableMultiMap<K, V> getSource() {
			return theSource;
		}

		protected BiFunction<K, ObservableCollection<V>, ObservableValue<V>> getValueMap() {
			return theValueMap;
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theSource.getKeyType();
		}

		@Override
		public TypeToken<V> getValueType() {
			return theSource.getValueType();
		}

		@Override
		public TypeToken<Map.Entry<K, V>> getEntryType() {
			return theEntryType;
		}

		@Override
		public boolean isLockSupported() {
			return theSource.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, boolean structural, Object cause) {
			return theSource.lock(write, structural, cause);
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public ObservableSet<K> keySet() {
			return theSource.keySet();
		}

		@Override
		public <K2> ObservableValue<V> observe(K2 key) {
			if (!theSource.keySet().belongs(key))
				return ObservableValue.of(getValueType(), null);
			return theValueMap.apply((K) key, theSource.get(key));
		}

		@Override
		public MapEntryHandle<K, V> getEntry(K key) {
			ObservableMultiEntry<K, V> outerHandle = theSource.getEntry(key);
			return outerHandle == null ? null : entryFor(outerHandle);
		}

		private MapEntryHandle<K, V> entryFor(ObservableMultiEntry<K, V> outerHandle) {
			ObservableValue<V> value = theValueMap.apply(outerHandle.getKey(), outerHandle.getValues());
			return new MapEntryHandle<K, V>() {
				@Override
				public ElementId getElementId() {
					return outerHandle.getElementId();
				}

				@Override
				public V get() {
					return value.get();
				}

				@Override
				public K getKey() {
					return outerHandle.getKey();
				}
			};
		}

		@Override
		public MapEntryHandle<K, V> getEntry(ElementId entryId) {
			return entryFor(theSource.getEntry(entryId));
		}

		@Override
		public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
			return mutableEntryFor(theSource.getEntry(entryId));
		}

		private MutableMapEntryHandle<K, V> mutableEntryFor(ObservableMultiEntry<K, V> outerHandle) {
			ObservableValue<V> mappedValue = theValueMap.apply(outerHandle.getKey(), outerHandle.getValues());
			return new MutableMapEntryHandle<K, V>() {
				@Override
				public K getKey() {
					return outerHandle.getKey();
				}

				@Override
				public ElementId getElementId() {
					return outerHandle.getElementId();
				}

				@Override
				public V get() {
					return mappedValue.get();
				}

				@Override
				public String isEnabled() {
					if (mappedValue instanceof SettableValue)
						return ((SettableValue<V>) mappedValue).isEnabled().get();
					else if (outerHandle.getValues().isEmpty())
						return null; // Unfortunately, there's no canAdd() method with no argument
					else if (outerHandle.getValues().size() == 1)
						return outerHandle.getValues().mutableElement(outerHandle.getValues().getElement(0).getElementId()).isEnabled();
					else
						return StdMsg.ELEMENT_EXISTS;
				}

				@Override
				public String isAcceptable(V value) {
					if (mappedValue instanceof SettableValue)
						return ((SettableValue<V>) value).isAcceptable(value);
					else if (outerHandle.getValues().isEmpty())
						return outerHandle.getValues().canAdd(value);
					else if (outerHandle.getValues().size() == 1)
						return outerHandle.getValues().mutableElement(outerHandle.getValues().getElement(0).getElementId())
							.isAcceptable(value);
					else
						return StdMsg.ELEMENT_EXISTS;
				}

				@Override
				public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
					if (mappedValue instanceof SettableValue)
						((SettableValue<V>) mappedValue).set(value, null);
					else if (outerHandle.getValues().isEmpty())
						outerHandle.getValues().add(value);
					else if (outerHandle.getValues().size() == 1)
						outerHandle.getValues().set(0, value);
					else
						throw new UnsupportedOperationException(StdMsg.ELEMENT_EXISTS);
				}

				@Override
				public String canRemove() {
					if (outerHandle.getValues().isEmpty())
						return StdMsg.UNSUPPORTED_OPERATION;
					MutableElementSpliterator<V> spliter = outerHandle.getValues().spliterator();
					String[] msg = new String[1];
					while (msg[0] == null && spliter.forElementM(el -> msg[0] = el.canRemove(), true)) {}
					return msg[0];
				}

				@Override
				public void remove() throws UnsupportedOperationException {
					String msg = canRemove();
					if (msg != null)
						throw new UnsupportedOperationException(msg);
					MutableElementSpliterator<V> spliter = outerHandle.getValues().spliterator();
					while (spliter.forElementM(el -> el.remove(), true)) {}
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
		public MapEntryHandle<K, V> putEntry(K key, V value) {
			try (Transaction t = lock(true, true, null)) {
				ObservableMultiEntry<K, V> outerHandle = theSource.getEntry(key);
				if (outerHandle != null) {
					ObservableValue<V> mappedValue = theValueMap.apply(outerHandle.getKey(), outerHandle.getValues());
					if (mappedValue instanceof SettableValue)
						((SettableValue<V>) mappedValue).set(value, null);
					else if (outerHandle.getValues().isEmpty())
						outerHandle.getValues().add(value);
					else if (outerHandle.getValues().size() == 1) {
						outerHandle.getValues().set(0, value);
					} else {
						// Here we can't be sure that anything we do will result in the correct value being populated,
						// short of clearing the collection and adding the value, which seems like overkill
						throw new UnsupportedOperationException(StdMsg.ELEMENT_EXISTS);
					}
					return entryFor(outerHandle);
				}
				ObservableValue<V> mappedValue = theValueMap.apply(key, theSource.get(key));
				if (mappedValue instanceof SettableValue)
					((SettableValue<V>) mappedValue).set(value, null);
				MultiMapEntryHandle<K, V> handle2 = theSource.putEntry(key, value, true);
				if (handle2 == null)
					return null;
				outerHandle = theSource.getEntry(handle2.getKeyId());
				return entryFor(outerHandle);
			}
		}

		@Override
		public V put(K key, V value) {
			try (Transaction t = theSource.lock(true, true, null)) {
				ObservableCollection<V> values = theSource.get(key);
				if (values.isEmpty()) {
					values.add(value);
					return null;
				} else
					return values.set(0, value);
			}
		}

		@Override
		public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
			boolean[] init = new boolean[1];
			CollectionSubscription sub = subscribe(evt -> {
				if (init[0])
					action.accept(evt);
			}, true);
			init[0] = true;
			return sub;
		}

		@Override
		public CollectionSubscription subscribe(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action, boolean forward) {
			class EntrySubscription {
				final K key;
				Subscription sub;
				V value;

				EntrySubscription(K key) {
					this.key = key;
				}
			}
			Map<ElementId, EntrySubscription> valueSubs = new HashMap<>();
			try (Transaction t = lock(false, null)) {
				Subscription outerSub = theSource.keySet().subscribe(evt -> {
					switch (evt.getType()) {
					case add:
						ObservableValue<V> value = theValueMap.apply(evt.getNewValue(), theSource.get(evt.getNewValue()));
						EntrySubscription entrySub = new EntrySubscription(evt.getNewValue());
						entrySub.sub = value.changes().act(valueEvt -> {
							CollectionChangeType mapEvtType;
							int index;
							if (valueEvt.isInitial()) {
								mapEvtType = CollectionChangeType.add;
								index = evt.getIndex();
							} else {
								mapEvtType = CollectionChangeType.set;
								index = theSource.keySet().getElementsBefore(evt.getElementId());
							}
							entrySub.value = valueEvt.getNewValue();
							action.accept(new ObservableMapEvent<>(evt.getElementId(), evt.getElementId(), //
								getKeyType(), getValueType(), index, 0, //
								mapEvtType, evt.getNewValue(), null, valueEvt.getNewValue(), valueEvt));
						});
						valueSubs.put(evt.getElementId(), entrySub);
						break;
					case remove:
						entrySub = valueSubs.remove(evt.getElementId());
						entrySub.sub.unsubscribe();
						action.accept(new ObservableMapEvent<>(evt.getElementId(), evt.getElementId(), //
							getKeyType(), getValueType(), evt.getIndex(), 0, //
							CollectionChangeType.remove, evt.getNewValue(), entrySub.value, entrySub.value, evt));
						break;
					case set:
						break;
					}
				}, forward);
				return removeAll -> {
					try (Transaction t2 = lock(false, false, null)) {
						outerSub.unsubscribe();
						for (EntrySubscription sub : valueSubs.values())
							sub.sub.unsubscribe();
						if (removeAll) {
							SimpleCause cause = new SimpleCause();
							try (Transaction ct = SimpleCause.use(cause)) {
								for (Map.Entry<ElementId, EntrySubscription> entry : valueSubs.entrySet()) {
									EntrySubscription sub = entry.getValue();
									entry.getValue().sub.unsubscribe();
									int index = theSource.keySet().getElementsBefore(entry.getKey());
									ObservableMapEvent<K, V> mapEvent = new ObservableMapEvent<>(entry.getKey(), entry.getKey(), //
										getKeyType(), getValueType(), index, 0, //
										CollectionChangeType.remove, sub.key, sub.value, sub.value, cause);
									try (Transaction mt = ObservableMapEvent.use(mapEvent)) {
										action.accept(mapEvent);
									}
								}
							}
						}
					}
				};
			}
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}

	interface MultiMapFlow<K, V> {
		<K2> MultiMapFlow<K2, V> withKeys(Function<UniqueDataFlow<?, ?, K>, UniqueDataFlow<?, ?, K2>> keyMap);

		<V2> MultiMapFlow<K, V2> withValues(Function<CollectionDataFlow<?, ?, V>, CollectionDataFlow<?, ?, V2>> valueMap);

		default MultiMapFlow<K, V> distinctForMap() {
			return distinctForMap(options -> {});
		}

		MultiMapFlow<K, V> distinctForMap(Consumer<UniqueOptions> options);

		MultiMapFlow<K, V> reverse();

		boolean supportsPassive();

		default boolean prefersPassive() {
			return supportsPassive();
		}

		default ObservableMultiMap<K, V> gather() {
			return gather(options -> {});
		}

		default ObservableMultiMap<K, V> gather(Consumer<GroupingOptions> options) {
			return gather(Observable.empty, options);
		}

		default ObservableMultiMap<K, V> gather(Observable<?> until) {
			return gather(until, options -> {});
		}

		ObservableMultiMap<K, V> gather(Observable<?> until, Consumer<GroupingOptions> options);
	}
}
