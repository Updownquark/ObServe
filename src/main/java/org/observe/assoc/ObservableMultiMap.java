package org.observe.assoc;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.UniqueDataFlow;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;
import org.qommons.Transaction;
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

	/** @return The {@link Equivalence} that is used by this multi-map's values */
	Equivalence<? super V> valueEquivalence();

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
		return ObservableCollection.flatten(entrySet().flow().map(collType).cache(false).map(entry -> entry.getValues()).collectLW());
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
			.map(entryType).cache(false).map(value -> new DefaultMapEntry(entry.getKey(), value)).collectLW()).collectLW());
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
			return theOuter.valueEquivalence();
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

	class MultiMapBuilder<K, V> {
		private final TypeToken<K> theKeyType;
		private final TypeToken<V> theValueType;
		private boolean isSafe = true;
		private Function<CollectionDataFlow<?, ?, V>, CollectionDataFlow<?, ?, V>> theValueMaker;

		public MultiMapBuilder(TypeToken<K> keyType, TypeToken<V> valueType) {
			theKeyType = keyType;
			theValueType = valueType;
		}

		public MultiMapBuilder<K, V> unsafe() {
			isSafe = false;
			return this;
		}

		public MultiMapBuilder<K, V> onValues(Function<CollectionDataFlow<?, ?, V>, CollectionDataFlow<?, ?, V>> valueMaker) {
			theValueMaker = valueMaker;
			return this;
		}

		public ObservableMultiMap<K, V> build() {
			return build(df -> df.unique(true));
		}

		public ObservableMultiMap<K, V> build(Function<? super CollectionDataFlow<?, ?, K>, ? extends UniqueDataFlow<?, ?, K>> keyMaker) {
			return new SimpleMultiMap<>(theKeyType, theValueType, isSafe, keyMaker, theValueMaker);
		}
	}

	class SimpleMultiMap<K, V> implements ObservableMultiMap<K, V> {
		private final TypeToken<K> theKeyType;
		private final TypeToken<V> theValueType;
		private final TypeToken<ObservableMultiEntry<K, V>> theEntryType;
		private final boolean isSafe;
		private final Function<CollectionDataFlow<?, ?, V>, CollectionDataFlow<?, ?, V>> theValueMaker;
		private final ObservableSet<K> theKeySet;
		private final ObservableSet<ObservableMultiEntry<K, V>> theEntrySet;

		public SimpleMultiMap(TypeToken<K> keyType, TypeToken<V> valueType, boolean safe,
			Function<? super CollectionDataFlow<?, ?, K>, ? extends UniqueDataFlow<?, ?, K>> keyMaker,
				Function<CollectionDataFlow<?, ?, V>, CollectionDataFlow<?, ?, V>> valueMaker) {
			theKeyType = keyType;
			theValueType = valueType;
			theEntryType = buildEntryType(keyType, valueType);
			isSafe = safe;
			theValueMaker = valueMaker;
			CollectionDataFlow<K, K, K> keySource = safe ? ObservableCollection.create(theKeyType)
				: ObservableCollection.createUnsafe(theKeyType, Collections.emptyList());
			theKeySet = keyMaker.apply(keySource).collect();
			theEntrySet = theKeySet.flow()//
				.mapEquivalent(theEntryType).cache(true).reEvalOnUpdate(false).map(this::createEntry, entry -> entry.getKey())//
				.filterModification().noAdd("Entries cannot be added directly").build()//
				.collect();
			// When an entry is removed, clear its contents
			theEntrySet.onChange(evt -> {
				switch (evt.getType()) {
				case add:
					break;
				case remove:
					try (Transaction t = evt.getOldValue().getValues().lock(true, evt)) {
						evt.getNewValue().getValues().clear();
					}
					break;
				case set:
					if (evt.getOldValue() != evt.getNewValue())
						try (Transaction t = evt.getOldValue().getValues().lock(true, evt)) {
							evt.getOldValue().getValues().clear();
						}
					break;
				}
			});
		}

		protected ObservableMultiEntry<K, V> createEntry(K key) {
			return new SimpleMultiEntry(key);
		}

		protected class SimpleMultiEntry implements ObservableMultiEntry<K, V> {
			private final K theKey;
			private final ObservableCollection<V> theValues;

			protected SimpleMultiEntry(K key) {
				theKey = key;
				CollectionDataFlow<?, ?, V> valueFlow = isSafe ? ObservableCollection.create(theValueType)
					: ObservableCollection.createUnsafe(theValueType, Collections.emptyList());
				if (theValueMaker != null)
					valueFlow = theValueMaker.apply(valueFlow);
				theValues = valueFlow.collect();
			}

			@Override
			public TypeToken<K> getKeyType() {
				return theKeyType;
			}

			@Override
			public K getKey() {
				return theKey;
			}

			@Override
			public ObservableCollection<V> getValues() {
				return theValues;
			}
		}
	}
}
