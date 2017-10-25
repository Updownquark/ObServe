package org.observe.assoc;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.CollectionSubscription;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.SubscriptionCause;
import org.observe.collect.ObservableSet;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
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
		return new TypeToken<Map.Entry<K, V>>() {}//
		.where(new TypeParameter<K>() {}, keyType.wrap())//
		.where(new TypeParameter<V>() {}, valueType.wrap());
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

	/**
	 * @param action The action to perform on map events
	 * @param forward Whether to subscribe to the map forward or reverse)
	 * @return The collection subscription to use to terminate listening
	 */
	default CollectionSubscription subscribe(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action, boolean forward) {
		try (Transaction t = lock(false, null)) {
			Subscription sub = onChange(action);
			SubscriptionCause.doWith(new SubscriptionCause(), c -> {
				int[] index = new int[] { forward ? 0 : size() - 1 };
				entrySet().spliterator(forward).forEachElement(entryEl -> {
					ObservableMapEvent.doWith(
						new ObservableMapEvent<>(entryEl.getElementId(), entryEl.getElementId(), getKeyType(), getValueType(), index[0],
							index[0], CollectionChangeType.add, entryEl.get().getKey(), null, entryEl.get().getValue(), c),
						action);
					if (forward)
						index[0]++;
					else
						index[0]--;
				}, forward);
			});
			return removeAll -> {
				if (!removeAll) {
					sub.unsubscribe();
					return;
				}
				try (Transaction unsubT = lock(false, null)) {
					sub.unsubscribe();
					SubscriptionCause.doWith(new SubscriptionCause(), c -> {
						int[] index = new int[] { forward ? 0 : size() - 1 };
						entrySet().spliterator(forward).forEachElement(entryEl -> {
							ObservableMapEvent.doWith(new ObservableMapEvent<>(entryEl.getElementId(), entryEl.getElementId(), getKeyType(),
								getValueType(), index[0], index[0], CollectionChangeType.remove, entryEl.get().getKey(),
								entryEl.get().getValue(), entryEl.get().getValue(), c), action);
							if (!forward)
								index[0]--;
						}, forward);
					});
				}
			};
		}
	}

	/**
	 * @param key The key to get the value for
	 * @param defValue A function producing a value to use for the given key in the case that the key is not present in this map
	 * @return An observable value that changes whenever the value for the given key changes in this map
	 */
	default <K2> ObservableValue<V> observe(K2 key, Function<? super K2, ? extends V> defValue) {
		if (!keySet().belongs(key))
			return ObservableValue.of(getValueType(), defValue.apply(key));
		return new ObservableValue<V>() {
			@Override
			public TypeToken<V> getType() {
				return getValueType();
			}

			@Override
			public Transaction lock() {
				return ObservableMap.this.lock(false, null);
			}

			@Override
			public V get() {
				CollectionElement<Map.Entry<K, V>> entryEl = entrySet().getElement(new SimpleMapEntry<>((K) key, null), true);
				if (entryEl != null)
					return entryEl.get().getValue();
				else
					return defValue.apply(key);
			}

			@Override
			public Observable<ObservableValueEvent<V>> changes() {
				return new Observable<ObservableValueEvent<V>>() {
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
								V oldValue = exists[0] ? evt.getOldValue() : defValue.apply(key);
								V newValue = newExists ? evt.getNewValue() : defValue.apply(key);
								exists[0] = newExists;
								observer.onNext(createChangeEvent(oldValue, newValue, evt));
							});
							CollectionElement<Map.Entry<K, V>> entryEl = entrySet().getElement(new SimpleMapEntry<>((K) key, null), true);
							exists[0] = entryEl != null;
							observer.onNext(createInitialEvent(exists[0] ? entryEl.get().getValue() : defValue.apply(key), null));
							return sub;
						}
					}

					@Override
					public boolean isSafe() {
						return true;
					}
				};
			}
		};
	}

	/**
	 * @param key The key to get the entry for
	 * @return An {@link java.util.Map.Entry} that represents the given key's presence in this map
	 */
	default Map.Entry<K, V> entryFor(K key) {
		CollectionElement<Map.Entry<K, V>> entryEl = entrySet().getElement(new SimpleMapEntry<>(key, null), true);
		return entryEl != null ? entryEl.get() : new SimpleMapEntry<>(key, null);
	}

	/** @return An observable collection of {@link java.util.Map.Entry observable entries} of all the key-value pairs stored in this map */
	default ObservableSet<Map.Entry<K, V>> observeEntries() {
		return keySet().flow().mapEquivalent(getEntryType(), this::entryFor, entry -> entry.getKey(), options -> options.cache(false))
			.collect();
	}

	/** @return An observable collection of all the values stored in this map */
	@Override
	default ObservableCollection<V> values() {
		return keySet().flow().flattenValues(getValueType(), k -> observe(k, k2 -> null)).collect();
	}

	@Override
	default int size() {
		return observeEntries().size();
	}

	@Override
	default boolean isEmpty() {
		return observeEntries().isEmpty();
	}

	@Override
	default boolean containsKey(Object key) {
		return keySet().contains(key);
	}

	@Override
	default boolean containsValue(Object value) {
		return keySet().stream().anyMatch(k -> equivalence().elementEquals(get(k), value));
	}

	@Override
	default V get(Object key) {
		return observe(key, k -> null).get();
	}

	@Override
	default ObservableSet<Entry<K, V>> entrySet() {
		return (ObservableSet<Entry<K, V>>) (ObservableSet<?>) observeEntries();
	}

	@Override
	default void putAll(Map<? extends K, ? extends V> m) {
		try (Transaction t = lock(true, true, null)) {
			for (Entry<? extends K, ? extends V> entry : m.entrySet())
				put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	default V remove(Object key) {
		V ret = get(key);
		keySet().remove(key);
		return ret;
	}

	@Override
	default void clear() {
		keySet().clear();
	}

	/**
	 * @return An observable that fires a (null) value whenever anything in this structure changes. This observable will only fire 1 event
	 *         per transaction.
	 */
	default Observable<Object> changes() {
		return values().simpleChanges();
	}

	static <K, V> ObservableMap<K, V> create(TypeToken<K> keyType, TypeToken<V> valueType) {
		return create(keyType, valueType, Equivalence.DEFAULT);
	}

	static <K, V> ObservableMap<K, V> create(TypeToken<K> keyType, TypeToken<V> valueType, Equivalence<Object> keyEquivalence) {
		return create(keyType, valueType, keyEquivalence, ObservableCollection.createDefaultBacking());
	}

	static <K, V> ObservableMap<K, V> create(TypeToken<K> keyType, TypeToken<V> valueType, Equivalence<? super K> keyEquivalence,
		BetterList<Map.Entry<K, V>> entryCollection) {
		TypeToken<Map.Entry<K, V>> entryType = new TypeToken<Map.Entry<K, V>>() {}.where(new TypeParameter<K>() {}, keyType.wrap())
			.where(new TypeParameter<V>() {}, valueType.wrap());
		ObservableSet<Map.Entry<K, V>> entrySet = ObservableCollection.create(entryType, entryCollection).flow().distinct().collect();
		ObservableSet<Map.Entry<K, V>> exposedEntrySet = entrySet.flow().filterMod(fm -> fm.noAdd(StdMsg.UNSUPPORTED_OPERATION))
			.collectPassive();
		ObservableSet<K> keySet = entrySet.flow().mapEquivalent(keyType, Map.Entry::getKey, key -> new SimpleMapEntry<>(key, null))
			.filterMod(fm -> fm.noAdd(StdMsg.UNSUPPORTED_OPERATION)).collectPassive();
		ReentrantLock valueLock = new ReentrantLock();
		Object[] firstCause = new Object[1];
		int[] causeHeight = new int[1];
		ListenerList<Consumer<? super ObservableMapEvent<? extends K, ? extends V>>> valueListeners = new ListenerList<>(
			"This map must not be modified in response to another modification");
		class MapEntry implements Map.Entry<K, V> {
			private final K theKey;
			private ElementId theElementId;
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

			@Override
			public V setValue(V value) {
				valueLock.lock();
				try {
					V oldValue = theValue;
					theValue = value;
					int index = entrySet.getElementsBefore(theElementId);
					ObservableMapEvent.doWith(
						new ObservableMapEvent<>(theElementId, theElementId, keyType, valueType, index, index, CollectionChangeType.set,
							theKey, oldValue, value, firstCause[0]), //
						evt -> valueListeners.forEach(//
							listener -> listener.accept(evt)));
					return oldValue;
				} finally {
					valueLock.unlock();
				}
			}
		}
		return new ObservableMap<K, V>() {
			@Override
			public boolean isLockSupported() {
				return keySet.isLockSupported();
			}

			@Override
			public Transaction lock(boolean write, boolean structural, Object cause) {
				Transaction entryLock = keySet.lock(write, structural, cause);
				valueLock.lock();
				boolean causeUsed = cause != null && causeHeight[0] == 0;
				if (causeUsed)
					firstCause[0] = cause;
				if (cause != null)
					causeHeight[0]++;
				return () -> {
					if (cause != null)
						causeHeight[0]--;
					if (causeUsed)
						firstCause[0] = null;
					valueLock.unlock();
					entryLock.close();
				};
			}

			@Override
			public TypeToken<K> getKeyType() {
				return keySet.getType();
			}

			@Override
			public TypeToken<V> getValueType() {
				return valueType;
			}

			@Override
			public TypeToken<Map.Entry<K, V>> getEntryType() {
				return entryType;
			}

			@Override
			public Equivalence<? super V> equivalence() {
				return Equivalence.DEFAULT;
			}

			@Override
			public ObservableSet<K> keySet() {
				return keySet;
			}

			@Override
			public MapEntryHandle<K, V> getEntry(K key) {
				CollectionElement<Map.Entry<K, V>> entryEl = entrySet.getElement(new SimpleMapEntry<>(key, null), true);
				return entryEl == null ? null : handleFor(entryEl);
			}

			@Override
			public MapEntryHandle<K, V> getEntry(ElementId entryId) {
				CollectionElement<Map.Entry<K, V>> entryEl = entrySet.getElement(entryId);
				return entryEl == null ? null : handleFor(entryEl);
			}

			@Override
			public MapEntryHandle<K, V> putEntry(K key, V value) {
				try (Transaction t = lock(true, true, null)) {
					CollectionElement<Map.Entry<K, V>> entryEl = entrySet.getElement(new SimpleMapEntry<>(key, null), true);
					if (entryEl != null) {
						entryEl.get().setValue(value);
						return handleFor(entryEl);
					}
					MapEntry newEntry = new MapEntry(key, value);
					entryEl = entrySet.addElement(newEntry, false);
					newEntry.theElementId = entryEl.getElementId();
					return handleFor(entryEl);
				}
			}

			@Override
			public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
				MutableCollectionElement<Map.Entry<K, V>> entryEl = entrySet.mutableElement(entryId);
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
						if (value != null && !valueType.getRawType().isInstance(value))
							return StdMsg.BAD_TYPE;
						return null;
					}

					@Override
					public void set(V value) throws UnsupportedOperationException, IllegalArgumentException {
						if (value != null && !valueType.getRawType().isInstance(value))
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
				Subscription entrySub = entrySet.onChange(evt -> {
					V oldValue = evt.getType() == CollectionChangeType.add ? null : evt.getNewValue().getValue();
					ObservableMapEvent
					.doWith(
						new ObservableMapEvent<>(evt.getElementId(), evt.getElementId(), keyType, valueType, evt.getIndex(),
							evt.getIndex(), evt.getType(), evt.getNewValue().getKey(), oldValue, evt.getNewValue().getValue(), evt),
						action);
				});
				Runnable valueSet = valueListeners.add(action, false);
				return () -> {
					valueSet.run();
					entrySub.unsubscribe();
				};
			}

			@Override
			public ObservableSet<java.util.Map.Entry<K, V>> entrySet() {
				return exposedEntrySet;
			}
		};
	}

	/**
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param keyType The key type for the map
	 * @param valueType The value type for the map
	 * @return An immutable, empty map with the given types
	 */
	static <K, V> ObservableMap<K, V> empty(TypeToken<K> keyType, TypeToken<V> valueType) {
		TypeToken<Map.Entry<K, V>> entryType = buildEntryType(keyType, valueType);
		ObservableSet<K> keySet = ObservableCollection.of(keyType).flow().distinct().collect();
		return new ObservableMap<K, V>() {
			@Override
			public TypeToken<K> getKeyType() {
				return keyType;
			}

			@Override
			public TypeToken<V> getValueType() {
				return valueType;
			}

			@Override
			public TypeToken<Map.Entry<K, V>> getEntryType() {
				return entryType;
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
				return keySet;
			}

			@Override
			public MapEntryHandle<K, V> putEntry(K key, V value) {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public MapEntryHandle<K, V> getEntry(K key) {
				return null;
			}

			@Override
			public MapEntryHandle<K, V> getEntry(ElementId entryId) {
				throw new NoSuchElementException();
			}

			@Override
			public MutableMapEntryHandle<K, V> mutableEntry(ElementId entryId) {
				throw new NoSuchElementException();
			}

			@Override
			public Subscription onChange(Consumer<? super ObservableMapEvent<? extends K, ? extends V>> action) {
				return () -> {};
			}

			@Override
			public String toString() {
				return entrySet().toString();
			}
		};
	}
}
