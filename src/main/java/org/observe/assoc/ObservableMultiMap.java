package org.observe.assoc;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionElementManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.CollectionManager;
import org.observe.collect.ObservableCollectionDataFlowImpl.FilterMapResult;
import org.observe.collect.ObservableCollectionImpl;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSetImpl;
import org.observe.collect.ObservableSortedSet;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.TransactableMultiMap;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * An observable map structure that allows more than one value to be stored per key
 *
 * @param <K> The type of key used by this map
 * @param <V> The type of values stored in this map
 */
public interface ObservableMultiMap<K, V> extends TransactableMultiMap<K, V> {
	/**
	 * A {@link java.util.Map.Entry} with observable capabilities
	 *
	 * @param <K> The type of key this entry uses
	 * @param <V> The type of value this entry stores
	 */
	public interface ObservableMultiEntry<K, V> extends MultiEntry<K, V> {
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
		static <K, V> ObservableMultiEntry<K, V> create(TypeToken<K> keyType, K key, Equivalence<? super K> keyEquivalence,
			ObservableCollection<V> values) {
			return new ObservableMultiEntry<K, V>() {
				@Override
				public TypeToken<K> getKeyType() {
					return keyType;
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

		/**
		 * Creates an empty multi-entry
		 *
		 * @param <K> The key type for the entry
		 * @param <V> The value type for the entry
		 * @param keyType The key type for the entry
		 * @param valueType The value type for the entry's values
		 * @param key The key for the entry
		 * @param keyEquivalence The key equivalence for the entry
		 * @return The new entry
		 */
		static <K, V> ObservableMultiEntry<K, V> empty(TypeToken<K> keyType, TypeToken<V> valueType, K key,
			Equivalence<? super K> keyEquivalence) {
			return create(keyType, key, keyEquivalence, ObservableCollection.constant(valueType).collect());
		}
	}

	/**
	 * A {@link ObservableMultiMap.ObservableMultiEntry} whose values are sorted
	 *
	 * @param <K> The type of key this entry uses
	 * @param <V> The type of value this entry stores
	 */
	public interface ValueSortedMultiEntry<K, V> extends ObservableMultiEntry<K, V>, ObservableSortedSet<V> {}

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
		return new TypeToken<ObservableMultiEntry<K, V>>() {}.where(new TypeParameter<K>() {}, keyType).where(new TypeParameter<V>() {},
			valueType);
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
	 * @param key The key to get the entry for
	 * @return A multi-entry that represents the given key's presence in this map. Never null.
	 */
	default ObservableMultiEntry<K, V> entryFor(K key) {
		return ObservableMultiEntry.create(getKeyType(), key, keySet().equivalence(), get(key));
	}

	/**
	 * @return An observable collection of {@link ObservableMultiEntry observable entries} of all the key-value set pairs stored in this map
	 */
	@Override
	default ObservableSet<ObservableMultiEntry<K, V>> entrySet() {
		return keySet().flow().mapEquivalent(getEntryType()).cache(false).map(this::entryFor, entry -> entry.getKey()).collectLW();
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

	/** @return All values stored in this map */
	@Override
	default ObservableCollection<V> values() {
		TypeToken<ObservableCollection<V>> collType = new TypeToken<ObservableCollection<V>>() {}.where(new TypeParameter<V>() {},
			getValueType());
		return ObservableCollection.flatten(entrySet().flow().map(collType).cache(false).map(entry -> entry.getValues()).collectLW())
			.collect();
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
		TypeToken<ObservableCollection<Map.Entry<K, V>>> entryCollectionType = new TypeToken<ObservableCollection<Map.Entry<K, V>>>() {}//
		.where(new TypeParameter<K>() {}, getKeyType()).where(new TypeParameter<V>() {}, getValueType());

		return ObservableCollection.flatten(entrySet().flow().map(entryCollectionType).cache(false).map(entry -> entry.getValues().flow()
			.map(entryType).cache(false).map(value -> new DefaultMapEntry(entry.getKey(), value)).collectLW()).collectLW()).collect();
	}

	/**
	 * @return An observable map with the same key set as this map and whose values are one of the elements in this multi-map for each key
	 */
	default ObservableMap<K, V> unique() {
		return new UniqueMap<>(this);
	}

	/**
	 * @return An observable that fires a value whenever anything in this structure changes. This observable will only fire 1 event per
	 *         transaction.
	 */
	default Observable<Object> changes() {
		return values().simpleChanges();
	}

	/** @return A multi-map data flow that may be used to produce derived maps whose data is based on this map's */
	default MultiMapFlow<K, K, V> flow() {
		return new DefaultMultiMapFlow<>(keySet(), keySet().flow(), getValueType(), key -> get(key).flow());
	}

	static <K, V> MultiMapFlow<?, K, V> create(TypeToken<K> keyType, TypeToken<V> valueType, Equivalence<? super K> keyEquivalence) {
		TypeToken<Map.Entry<K, V>> entryType=new TypeToken<Map.Entry<K, V>>(){}.where(new TypeParameter<K>(){}, keyType).
			where(new TypeParameter<V>(){}, valueType);
		class MapEntry implements Map.Entry<K, V> {
			private final K theKey;
			private V theValue;

			public MapEntry(K key, V value) {
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
				V old = theValue;
				theValue = value;
				return old;
			}

			@Override
			public int hashCode() {
				return Objects.hashCode(theKey);
			}

			@Override
			public boolean equals(Object obj) {
				return keyEquivalence.elementEquals(theKey, obj);
			}

			@Override
			public String toString() {
				return theKey + "=" + theValue;
			}
		}
		ObservableCollection<Map.Entry<K, V>> simpleEntryCollection=ObservableCollection.create(entryType).collect();
		ObservableSet<K> keySet = simpleEntryCollection.flow().map(keyType).map(Map.Entry::getKey).withEquivalence(keyEquivalence)
			.unique(true).collect();
		return new DefaultMultiMapFlow<>(keySet, keySet.flow(), valueType, key -> simpleEntryCollection.flow()//
			.filterStatic(entry -> keyEquivalence.elementEquals(entry.getKey(), key) ? null : StdMsg.WRONG_GROUP)//
			.map(valueType).cache(false).withReverse(value -> new MapEntry(key, value)).map(Map.Entry::getValue));
	}

	/**
	 * Implements {@link ObservableMultiMap#unique()}
	 *
	 * @param <K> The key-type of the map
	 * @param <V> The value-type of the map
	 */
	class UniqueMap<K, V> implements ObservableMap<K, V> {
		private final ObservableMultiMap<K, V> theOuter;
		private final TypeToken<ObservableEntry<K, V>> theEntryType;

		public UniqueMap(ObservableMultiMap<K, V> outer) {
			theOuter = outer;
			theEntryType = ObservableMap.buildEntryType(getKeyType(), getValueType());
		}

		protected ObservableMultiMap<K, V> getOuter() {
			return theOuter;
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theOuter.getKeyType();
		}

		@Override
		public TypeToken<V> getValueType() {
			return theOuter.getValueType();
		}

		@Override
		public TypeToken<ObservableEntry<K, V>> getEntryType() {
			return theEntryType;
		}

		@Override
		public boolean isLockSupported() {
			return theOuter.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theOuter.lock(write, cause);
		}

		@Override
		public Equivalence<? super V> equivalence() {
			return Equivalence.DEFAULT;
		}

		@Override
		public ObservableSet<K> keySet() {
			return theOuter.keySet();
		}

		@Override
		public ObservableValue<V> observe(Object key) {
			return theOuter.get(key).observeFind(value -> true, () -> null, true);
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}
	}

	interface MultiMapFlow<OK, K, V> {
		UniqueDataFlow<OK, ?, K> keys();
		TypeToken<V> getTargetValueType();

		/**
		 * @param <K2> The type of the new map key
		 * @param keyFlow Produces a new key flow from this map flow's keys. Although this cannot be checked, there is an implied assertion
		 *        that the resulting key flow will be equivalent to this flow's keys. For example, the equivalence of the key flow cannot be
		 *        switched. If this requirement is not met, errors are possible.
		 * @return A new multi-map flow with a different set of keys
		 */
		<K2> MultiMapFlow<OK, K2, V> onEquivalentKeys(
			Function<? super UniqueDataFlow<OK, ?, K>, ? extends UniqueDataFlow<OK, ?, K2>> keyFlow);

		<V2> MultiMapFlow<OK, K, V2> onValues(TypeToken<V2> targetType,
			Function<? super CollectionDataFlow<?, ?, V>, ? extends CollectionDataFlow<?, ?, V2>> valueFlow);

		ObservableMultiMap<K, V> collectLW();

		default ObservableMultiMap<K, V> collect() {
			return collect(Observable.empty);
		}
		ObservableMultiMap<K, V> collect(Observable<?> until);
	}

	class DefaultMultiMapFlow<OK, K, V> implements MultiMapFlow<OK, K, V> {
		private final ObservableCollection<OK> theKeyCollection;
		private final UniqueDataFlow<OK, ?, K> theKeyFlow;
		private final TypeToken<V> theValueType;
		private final Function<? super OK, CollectionDataFlow<?, ?, V>> theValueMaker;

		public DefaultMultiMapFlow(ObservableCollection<OK> keyCollection, UniqueDataFlow<OK, ?, K> keyFlow, TypeToken<V> valueType,
			Function<? super OK, CollectionDataFlow<?, ?, V>> valueMaker) {
			theKeyCollection = keyCollection;
			theKeyFlow = keyFlow;
			theValueType = valueType;
			theValueMaker = valueMaker;
		}

		protected ObservableCollection<OK> getKeyCollection() {
			return theKeyCollection;
		}

		protected Function<? super OK, CollectionDataFlow<?, ?, V>> getValueMaker() {
			return theValueMaker;
		}

		@Override
		public UniqueDataFlow<OK, ?, K> keys() {
			return theKeyFlow;
		}

		@Override
		public TypeToken<V> getTargetValueType() {
			return theValueType;
		}

		@Override
		public <K2> MultiMapFlow<OK, K2, V> onEquivalentKeys(
			Function<? super UniqueDataFlow<OK, ?, K>, ? extends UniqueDataFlow<OK, ?, K2>> keyFlow) {
			UniqueDataFlow<OK, ?, K2> newKeys = keyFlow.apply(theKeyFlow);
			return new DefaultMultiMapFlow<>(theKeyCollection, newKeys, theValueType, theValueMaker);
		}

		@Override
		public <V2> MultiMapFlow<OK, K, V2> onValues(TypeToken<V2> targetType,
			Function<? super CollectionDataFlow<?, ?, V>, ? extends CollectionDataFlow<?, ?, V2>> valueFlow) {
			Function<? super OK, CollectionDataFlow<?, ?, V2>> newValues = theValueMaker.andThen(valueFlow);
			return new DefaultMultiMapFlow<>(theKeyCollection, theKeyFlow, targetType, newValues);
		}

		@Override
		public ObservableMultiMap<K, V> collectLW() {
			return collect(true, Observable.empty);
		}

		@Override
		public ObservableMultiMap<K, V> collect(Observable<?> until) {
			return collect(false, until);
		}

		protected ObservableMultiMap<K, V> collect(boolean lightWeight, Observable<?> until) {
			return new DerivedMultiMap<>(theKeyCollection, theKeyFlow, until, theValueType, theValueMaker, lightWeight);
		}
	}

	class DerivedMultiMap<OK, K, V> implements ObservableMultiMap<K, V> {
		private final ObservableCollection<OK> theSource;
		private final CollectionManager<OK, ?, K> theKeyManager;
		private final TypeToken<V> theValueType;
		private final TypeToken<ObservableMultiEntry<K, V>> theEntryType;
		private final TypeToken<ObservableCollection<V>> theValueCollectionType;
		private final Function<? super OK, CollectionDataFlow<?, ?, V>> theValueMaker;
		private final Observable<?> theUntil;
		private final boolean isLightWeight;

		private final DerivedEntrySet theEntries;

		// These transient value variables help reduce the duplicate creation of value collections, which may be expensive
		private final Map<OK, Reference<ObservableCollection<V>>> theTransientValues;
		private final ReferenceQueue<ObservableCollection<V>> theGCdTransientValues;
		private final Map<Reference<ObservableCollection<V>>, K> theTransientKeysByReference;
		private final Lock theTransientValueLock;

		private final ObservableCollection<V> empty;

		public DerivedMultiMap(ObservableCollection<OK> keySource, UniqueDataFlow<OK, ?, K> keyFlow, Observable<?> until,
			TypeToken<V> valueType, Function<? super OK, CollectionDataFlow<?, ?, V>> valueMaker, boolean lightWeight) {
			theSource = keySource;
			theValueType = valueType;
			theValueMaker = valueMaker;
			theUntil = until;
			isLightWeight = lightWeight;
			theEntryType = buildEntryType(keyFlow.getTargetType(), valueType);
			theValueCollectionType = new TypeToken<ObservableCollection<V>>() {}.where(new TypeParameter<V>() {}, theValueType);

			theTransientValues = keySource.equivalence().createMap();
			theGCdTransientValues = new ReferenceQueue<>();
			theTransientKeysByReference = new IdentityHashMap<>();
			theTransientValueLock = new ReentrantLock(); // Don't need the reentrancy, but whatever

			empty = ObservableCollection.constant(theValueType).collect();

			theKeyManager = keyFlow.manageCollection();

			// Need to create the entries last because the constructor will cause all the initial entries to be created, and those entries
			// need access to this class's initialized fields
			theEntries = createEntrySet(theSource);
		}

		protected CollectionManager<OK, ?, K> getKeyManager() {
			return theKeyManager;
		}

		protected Observable<?> getUntil() {
			return theUntil;
		}

		protected boolean isLightWeight() {
			return isLightWeight;
		}

		@Override
		public boolean isLockSupported() {
			return theEntries.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			// We'll assume locking the key set will propagate to the values.
			// If this is untrue, then the value collections would be unaffected by this, of course;
			// but if it is true, then iterating through each key and locking the values would be wasted linear time
			return theEntries.lock(write, cause);
		}

		@Override
		public TypeToken<K> getKeyType() {
			return theEntries.getType();
		}

		@Override
		public TypeToken<V> getValueType() {
			return theValueType;
		}

		@Override
		public TypeToken<ObservableMultiEntry<K, V>> getEntryType() {
			return theEntryType;
		}

		@Override
		public ObservableSet<K> keySet() {
			return theEntries;
		}

		@Override
		public ObservableCollection<V> get(Object key) {
			if (!theEntries.belongs(key))
				return empty; // The belongs method is assumed to be stateless, so this will always be empty
			return ObservableCollection.flattenValue(theEntries.observeElement((K) key, true).mapV(theValueCollectionType, el -> {
				if (el == null) {
					// Means there are currently no values for the given key.
					FilterMapResult<K, OK> reversedKey = theKeyManager.reverse((K) key);
					if (reversedKey.error != null) {
						// We can't call the value maker. We'll include the message that says why.
						return empty.flow().filterModification().immutable(reversedKey.error, false).build().collectLW();
					}
					return valuesFor(reversedKey.result);
				}
				else
					return ((DerivedEntrySet.DerivedEntryElement) el.getElementId()).getValues();
			}));
		}

		protected ObservableCollection<V> valuesFor(OK key) {
			theTransientValueLock.lock();
			try {
				// See if these values are already cached
				ObservableCollection<V> values = null;
				boolean wasCached = false;
				Reference<ObservableCollection<V>> ref = theTransientValues.get(key);
				if (ref != null) {
					values = ref.get();
					wasCached = values != null;
					if (!wasCached)
						theTransientValues.remove(key); // Remove the GC'd entry
				}
				if (values == null) {
					// We'll make the value collection (which should be empty initially) that may allow adding values
					CollectionDataFlow<?, ?, V> valueFlow = theValueMaker.apply(key);
					values = isLightWeight ? valueFlow.collectLW() : valueFlow.collect(theUntil);
					// We'll cache these values and re-use them in case the user adds values to them, which presumably
					// will propagate into adding a key for the entry and making it pop up in the key/entry set.
					theTransientValues.put(key, new WeakReference<>(values, theGCdTransientValues));
				}

				// Before we leave, we need to take out the trash-- transient values may have been GC'd, but their keys are still in the map
				Reference<? extends ObservableCollection<V>> removedValues = theGCdTransientValues.poll();
				while (removedValues != null) {
					theTransientValues.remove(theTransientKeysByReference.remove(removedValues));
					removedValues = theGCdTransientValues.poll();
				}

				return values;
			} finally {
				theTransientValueLock.unlock();
			}
		}

		protected DerivedEntrySet createEntrySet(ObservableCollection<OK> keySource) {
			return new DerivedEntrySet(keySource, theKeyManager, theUntil);
		}

		protected class DerivedEntrySet extends ObservableSetImpl.DerivedSet<OK, K> {
			DerivedEntrySet(ObservableCollection<OK> keySource, CollectionManager<OK, ?, K> flow, Observable<?> until) {
				super(keySource, flow, until);
			}

			protected class DerivedEntryElement extends ObservableCollectionImpl.DerivedCollection.DerivedCollectionElement<OK, K> {
				private final ObservableCollection<V> theValues;

				protected DerivedEntryElement(CollectionElementManager<OK, ?, K> manager, OK initValue) {
					super(manager, initValue);
					// Note: Here we have to assume that the value flow is itself aware of changes to the keys.
					// If this key was replaced with a non-equivalent key, for example, the values will not change unless the value flow
					// does this.
					// Similarly for an key update--if the underlying flow doesn't know about updates to the keys, the values won't
					// be updated.
					theValues = valuesFor(initValue);
				}

				protected ObservableCollection<V> getValues() {
					return theValues;
				}
			}
		}
	}
}
