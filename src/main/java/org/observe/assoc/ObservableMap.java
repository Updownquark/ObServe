package org.observe.assoc;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.qommons.Transaction;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A map with observable capabilities
 *
 * @param <K> The type of keys this map uses
 * @param <V> The type of values this map stores
 */
public interface ObservableMap<K, V> extends TransactableMap<K, V> {
	/**
	 * A {@link java.util.Map.Entry} with observable capabilities. The {@link #equals(Object) equals} and {@link #hashCode() hashCode}
	 * methods of this class must use only the entry's key.
	 *
	 * @param <K> The type of key this entry uses
	 * @param <V> The type of value this entry stores
	 */
	public interface ObservableEntry<K, V> extends Entry<K, V>, ObservableValue<V> {
		@Override
		default V get() {
			return getValue();
		}

		/**
		 * @param type The value type for the entry
		 * @param key The key for the entry
		 * @param value The value for the entry
		 * @return A constant-value observable entry
		 */
		public static <K, V> ObservableEntry<K, V> constEntry(TypeToken<V> type, K key, V value) {
			class ObservableKeyEntry implements ObservableEntry<K, V> {
				@Override
				public TypeToken<V> getType() {
					return type;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<V>> observer) {
					observer.onNext(createInitialEvent(value));
					return () -> {
					};
				}

				@Override
				public boolean isSafe() {
					return true;
				}

				@Override
				public K getKey() {
					return key;
				}

				@Override
				public V getValue() {
					return value;
				}

				@Override
				public V setValue(V value2) {
					return value;
				}

				@Override
				public int hashCode() {
					return Objects.hashCode(key);
				}

				@Override
				public boolean equals(Object obj) {
					return obj instanceof Map.Entry && Objects.equals(((Map.Entry<?, ?>) obj).getKey(), key);
				}
			}
			return new ObservableKeyEntry();
		}

		/**
		 * @param entry The entry to flatten
		 * @return An Observable entry whose value is the value of the observable value in the given entry
		 */
		static <K, V> ObservableEntry<K, V> flatten(ObservableEntry<K, ? extends ObservableValue<V>> entry) {
			class FlattenedObservableEntry extends SettableValue.SettableFlattenedObservableValue<V> implements ObservableEntry<K, V> {
				FlattenedObservableEntry(ObservableValue<? extends ObservableValue<? extends V>> value,
					Supplier<? extends V> defaultValue) {
					super(value, defaultValue);
				}

				@Override
				public K getKey() {
					return entry.getKey();
				}

				@Override
				public V getValue() {
					return get();
				}

				@Override
				public V setValue(V value) {
					return set(value, null);
				}

				@Override
				public int hashCode() {
					return Objects.hashCode(getKey());
				}

				@Override
				public boolean equals(Object obj) {
					return obj instanceof Map.Entry && Objects.equals(((Map.Entry<?, ?>) obj).getKey(), getKey());
				}
			}
			return new FlattenedObservableEntry(entry, () -> null);
		}
	}

	/** @return The type of keys this map uses */
	TypeToken<K> getKeyType();

	/** @return The type of values this map stores */
	TypeToken<V> getValueType();

	/**
	 * @return The observable value for the current session of this map. The session allows listeners to retain state for the duration of a
	 *         unit of work (controlled by implementation-specific means), batching events where possible. Not all events on a map will have
	 *         a session (the value may be null). In addition, the presence or absence of a session need not imply anything about the
	 *         threaded interactions with a session. A transaction may encompass events fired and received on multiple threads. In short,
	 *         the only thing guaranteed about sessions is that they will end. Therefore, if a session is present, observers may assume that
	 *         they can delay expensive results of map events until the session completes. The {@link ObservableCollection#getSession()
	 *         sessions} of the {@link #observeEntries() entries}, {@link #keySet() keys}, and {@link #values() values} collections should
	 *         be the same as this one.
	 */
	ObservableValue<CollectionSession> getSession();

	@Override
	ObservableSet<K> keySet();

	/**
	 * <p>
	 * A default implementation of {@link #keySet()}.
	 * </p>
	 * <p>
	 * No {@link ObservableMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #observeEntries()} methods. {@link #defaultObserveEntries(ObservableMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(ObservableMap)} or {@link #defaultObserve(ObservableMap, Object)}. Either {@link #observeEntries()} or both
	 * {@link #keySet()} and {@link #get(Object)} must be custom. If an implementation supplies custom {@link #keySet()} and
	 * {@link #get(Object)} implementations, it may use {@link #defaultObserveEntries(ObservableMap)} for its {@link #observeEntries()} . If
	 * an implementation supplies a custom {@link #observeEntries()} implementation, it may use {@link #defaultKeySet(ObservableMap)} and
	 * {@link #defaultObserve(ObservableMap, Object)} for its {@link #keySet()} and {@link #get(Object)} implementations, respectively.
	 * Using default implementations for both will result in infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create a key set for
	 * @return A key set for the map
	 */
	public static <K, V> ObservableSet<K> defaultKeySet(ObservableMap<K, V> map) {
		return ObservableSet.unique(map.observeEntries().map(Entry::getKey), Objects::equals);
	}

	/**
	 * @param key The key to get the value for
	 * @return An observable value that changes whenever the value for the given key changes in this map
	 */
	ObservableValue<V> observe(Object key);

	/**
	 * <p>
	 * A default implementation of {@link #observe(Object)}.
	 * </p>
	 * <p>
	 * No {@link ObservableMultiMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #observeEntries()} methods. {@link #defaultObserveEntries(ObservableMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(ObservableMap)} or {@link #defaultObserve(ObservableMap, Object)}. Either {@link #observeEntries()} or both
	 * {@link #keySet()} and {@link #get(Object)} must be custom. If an implementation supplies custom {@link #keySet()} and
	 * {@link #get(Object)} implementations, it may use {@link #defaultObserveEntries(ObservableMap)} for its {@link #observeEntries()} . If
	 * an implementation supplies a custom {@link #observeEntries()} implementation, it may use {@link #defaultKeySet(ObservableMap)} and
	 * {@link #defaultObserve(ObservableMap, Object)} for its {@link #keySet()} and {@link #get(Object)} implementations, respectively.
	 * Using default implementations for both will result in infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to observe the value in
	 * @param key The key to observe the value of
	 * @return An observable value representing the value of the given key in this map
	 */
	public static <K, V> ObservableValue<V> defaultObserve(ObservableMap<K, V> map, Object key) {
		Map.Entry<Object, Object> keyEntry = new Map.Entry<Object, Object>() {
			@Override
			public Object getKey() {
				return key;
			}

			@Override
			public Object getValue() {
				return null;
			}

			@Override
			public Object setValue(Object value) {
				return null;
			}

			@Override
			public int hashCode() {
				return Objects.hashCode(key);
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof Map.Entry && Objects.equals(((Map.Entry<?, ?>) obj).getKey(), key);
			}

			@Override
			public String toString() {
				return String.valueOf(key);
			}
		};
		return ObservableValue.flatten(map.observeEntries().equivalent(keyEntry));
	}

	/**
	 * @param key The key to get the entry for
	 * @return An {@link ObservableEntry} that represents the given key's presence in this map
	 */
	default ObservableEntry<K, V> entryFor(K key){
		ObservableValue<V> value=observe(key);
		if(value instanceof SettableValue)
			return new SettableEntry<>(key, (SettableValue<V>) value);
		else
			return new ObsEntryImpl<>(key, value);
	}

	/** @return An observable collection of {@link ObservableEntry observable entries} of all the key-value pairs stored in this map */
	ObservableSet<? extends ObservableEntry<K, V>> observeEntries();

	/**
	 * <p>
	 * A default implementation of {@link #observeEntries()}.
	 * </p>
	 * <p>
	 * No {@link ObservableMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #observeEntries()} methods. {@link #defaultObserveEntries(ObservableMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(ObservableMap)} or {@link #defaultObserve(ObservableMap, Object)}. Either {@link #observeEntries()} or both
	 * {@link #keySet()} and {@link #get(Object)} must be custom. If an implementation supplies custom {@link #keySet()} and
	 * {@link #get(Object)} implementations, it may use {@link #defaultObserveEntries(ObservableMap)} for its {@link #observeEntries()} . If
	 * an implementation supplies a custom {@link #observeEntries()} implementation, it may use {@link #defaultKeySet(ObservableMap)} and
	 * {@link #defaultObserve(ObservableMap, Object)} for its {@link #keySet()} and {@link #get(Object)} implementations, respectively.
	 * Using default implementations for both will result in infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create an entry set for
	 * @return An entry set for the map
	 */
	public static <K, V> ObservableSet<? extends ObservableEntry<K, V>> defaultObserveEntries(ObservableMap<K, V> map) {
		return ObservableSet.unique(map.keySet().map(map::entryFor), (entry1, entry2) -> map.keySet().getEqualizer()
			.equals(((ObservableEntry<K, V>) entry1).getKey(), ((ObservableEntry<K, V>) entry2).getKey()));
	}

	/** @return Whether this map is thread-safe, meaning it is constrained to only fire events on a single thread at a time */
	boolean isSafe();

	// TODO default ObservableGraph<N, E> safe(){}

	/** @return An observable value reflecting the number of key-value pairs stored in this map */
	default ObservableValue<Integer> observeSize() {
		return keySet().observeSize();
	}

	/** @return An observable collection of all the values stored in this map */
	@Override
	default ObservableCollection<V> values() {
		TypeToken<ObservableValue<V>> obValType = new TypeToken<ObservableValue<V>>() {}.where(new TypeParameter<V>() {}, getValueType());
		return ObservableCollection.flattenValues(keySet().map(obValType, this::observe));
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
		return observeEntries().find(entry -> java.util.Objects.equals(entry.getKey(), key)).get() != null;
	}

	@Override
	default boolean containsValue(Object value) {
		return observeEntries().find(entry -> java.util.Objects.equals(entry.getValue(), value)).get() != null;
	}

	@Override
	default V get(Object key) {
		return observe(key).get();
	}

	@Override
	default ObservableSet<Entry<K, V>> entrySet() {
		return (ObservableSet<Entry<K, V>>) (ObservableSet<?>) observeEntries();
	}

	/**
	 * @return An observable that fires a (null) value whenever anything in this structure changes. This observable will only fire 1 event
	 *         per transaction.
	 */
	default Observable<ObservableValueEvent<?>> changes() {
		return keySet().refreshEach(this::observe).simpleChanges();
	}

	/**
	 * @param <T> The type of values to map to
	 * @param map The function to map values
	 * @return A map with the same key set, but with its values mapped according to the given mapping function
	 */
	default <T> ObservableMap<K, T> map(Function<? super V, T> map) {
		ObservableMap<K, V> outer = this;
		return new ObservableMap<K, T>() {
			private TypeToken<T> theValueType = (TypeToken<T>) TypeToken.of(map.getClass())
				.resolveType(Function.class.getTypeParameters()[1]);

			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
			}

			@Override
			public boolean isSafe() {
				return outer.isSafe();
			}

			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<T> getValueType() {
				return theValueType;
			}

			@Override
			public ObservableSet<K> keySet() {
				return outer.keySet();
			}

			@Override
			public ObservableValue<T> observe(Object key) {
				return outer.observe(key).mapV(map);
			}

			@Override
			public ObservableSet<? extends ObservableEntry<K, T>> observeEntries() {
				return ObservableMap.defaultObserveEntries(this);
			}

			@Override
			public String toString() {
				return entrySet().toString();
			}
		};
	}

	/**
	 * @param keyFilter The filter to pare down this map's keys
	 * @return A map that has the same content as this map, except for the keys filtered out by the key filter
	 */
	default ObservableMap<K, V> filterKeys(Predicate<? super K> keyFilter) {
		ObservableMap<K, V> outer = this;
		return new ObservableMap<K, V>() {
			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<V> getValueType() {
				return outer.getValueType();
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
			}

			@Override
			public ObservableSet<K> keySet() {
				return outer.keySet().filter(keyFilter);
			}

			@Override
			public ObservableValue<V> observe(Object key) {
				if (getKeyType().getRawType().isInstance(key) && keyFilter.test((K) key))
					return outer.observe(key);
				else
					return ObservableValue.constant(getValueType(), null);
			}

			@Override
			public ObservableSet<? extends ObservableEntry<K, V>> observeEntries() {
				return outer.observeEntries().filter(entry -> keyFilter.test(entry.getKey()));
			}

			@Override
			public boolean isSafe() {
				return outer.isSafe();
			}
		};
	}

	/**
	 * @param keyFilter The filter to pare down this map's keys
	 * @return A map that has the same content as this map, except for the keys filtered out by the key filter
	 */
	default ObservableMap<K, V> filterKeysStatic(Predicate<? super K> keyFilter) {
		ObservableMap<K, V> outer = this;
		return new ObservableMap<K, V>() {
			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<V> getValueType() {
				return outer.getValueType();
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
			}

			@Override
			public ObservableSet<K> keySet() {
				return outer.keySet().filterStatic(keyFilter);
			}

			@Override
			public ObservableValue<V> observe(Object key) {
				if (getKeyType().getRawType().isInstance(key) && keyFilter.test((K) key))
					return outer.observe(key);
				else
					return ObservableValue.constant(getValueType(), null);
			}

			@Override
			public ObservableSet<? extends ObservableEntry<K, V>> observeEntries() {
				return outer.observeEntries().filterStatic(entry -> keyFilter.test(entry.getKey()));
			}

			@Override
			public boolean isSafe() {
				return outer.isSafe();
			}
		};
	}

	/** @return An immutable copy of this map */
	default ObservableMap<K, V> immutable() {
		ObservableMap<K, V> outer = this;
		class Immutable implements ObservableMap<K, V> {
			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<V> getValueType() {
				return outer.getValueType();
			}

			@Override
			public ObservableSet<K> keySet() {
				return outer.keySet().immutable();
			}

			@Override
			public ObservableCollection<V> values() {
				return outer.values().immutable();
			}

			@Override
			public ObservableSet<? extends ObservableEntry<K, V>> observeEntries() {
				return outer.observeEntries().immutable();
			}

			@Override
			public ObservableValue<V> observe(Object key) {
				ObservableValue<V> val = outer.observe(key);
				if(val instanceof SettableValue)
					return ((SettableValue<V>) val).unsettable();
				else
					return val;
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public boolean isSafe() {
				return outer.isSafe();
			}

			@Override
			public String toString() {
				return entrySet().toString();
			}
		}
		return new Immutable();
	}

	/**
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param keyType The key type for the map
	 * @param valueType The value type for the map
	 * @return An immutable, empty map with the given types
	 */
	static <K, V> ObservableMap<K, V> empty(TypeToken<K> keyType, TypeToken<V> valueType) {
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
			public ObservableValue<CollectionSession> getSession() {
				return ObservableValue.constant(TypeToken.of(CollectionSession.class), null);
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return () -> {
				};
			}

			@Override
			public boolean isSafe() {
				return true;
			}

			@Override
			public ObservableSet<K> keySet() {
				return ObservableSet.constant(keyType);
			}

			@Override
			public ObservableValue<V> observe(Object key) {
				return ObservableValue.constant(valueType, null);
			}

			@Override
			public ObservableSet<? extends ObservableEntry<K, V>> observeEntries() {
				return ObservableMap.defaultObserveEntries(this);
			}

			@Override
			public String toString() {
				return entrySet().toString();
			}
		};
	}

	@Override
	default V put(K key, V value) {
		ObservableEntry<K, V> entry = entryFor(key);
		if(entry instanceof SettableValue)
			return ((SettableValue<V>) entry).set(value, null);
		else
			throw new UnsupportedOperationException();
	}

	@Override
	default V remove(Object key) {
		V ret=get(key);
		keySet().remove(key);
		return ret;
	}

	@Override
	default void putAll(Map<? extends K, ? extends V> m) {
		for(Entry<? extends K, ? extends V> entry : m.entrySet())
			put(entry.getKey(), entry.getValue());
	}

	@Override
	default void clear() {
		keySet().clear();
	}

	/**
	 * @param map The map to flatten
	 * @return A map whose values are the values of this map's observable values
	 */
	public static <K, V> ObservableMap<K, V> flatten(ObservableMap<K, ? extends ObservableValue<? extends V>> map) {
		return new ObservableMap<K, V>() {
			@Override
			public boolean isSafe() {
				return false;
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return ObservableValue.constant(TypeToken.of(CollectionSession.class), null);
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return map.lock(write, cause);
			}

			@Override
			public TypeToken<K> getKeyType() {
				return map.getKeyType();
			}

			@Override
			public TypeToken<V> getValueType() {
				return (TypeToken<V>) map.getValueType().resolveType(ObservableValue.class.getTypeParameters()[0]);
			}

			@Override
			public ObservableSet<K> keySet() {
				return map.keySet();
			}

			@Override
			public ObservableValue<V> observe(Object key) {
				return ObservableValue.flatten(map.observe(key));
			}

			@Override
			public ObservableSet<? extends ObservableEntry<K, V>> observeEntries() {
				return map.observeEntries().mapEquivalent(entry -> (ObservableEntry<K, V>) ObservableEntry.flatten(entry), null);
			}
		};
	}

	/**
	 * A simple entry implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsEntryImpl<K, V> implements ObservableEntry<K, V> {
		private final K theKey;

		private final ObservableValue<V> theValue;

		ObsEntryImpl(K key, ObservableValue<V> value) {
			theKey = key;
			theValue = value;
		}

		protected ObservableValue<V> getWrapped() {
			return theValue;
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public V getValue() {
			return theValue.get();
		}

		@Override
		public V setValue(V value) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isSafe() {
			return theValue.isSafe();
		}

		@Override
		public TypeToken<V> getType() {
			return theValue.getType();
		}

		@Override
		public Subscription subscribe(Observer<? super ObservableValueEvent<V>> observer) {
			return theValue.subscribe(observer);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theKey);
		}

		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			return obj instanceof ObsEntryImpl && Objects.equals(theKey, ((ObsEntryImpl<?, ?>) obj).theKey);
		}

		@Override
		public String toString() {
			return theKey + "=" + theValue.get();
		}
	}

	/**
	 * A simple settable entry implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class SettableEntry<K, V> extends ObsEntryImpl<K, V> implements SettableValue<V> {
		public SettableEntry(K key, SettableValue<V> value) {
			super(key, value);
		}

		@Override
		protected SettableValue<V> getWrapped() {
			return (SettableValue<V>) super.getWrapped();
		}

		@Override
		public <V2 extends V> V set(V2 value, Object cause) throws IllegalArgumentException {
			return getWrapped().set(value, cause);
		}

		@Override
		public <V2 extends V> String isAcceptable(V2 value) {
			return getWrapped().isAcceptable(value);
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return getWrapped().isEnabled();
		}
	}
}
