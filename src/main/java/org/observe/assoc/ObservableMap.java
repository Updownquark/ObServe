package org.observe.assoc;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.Equivalence;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.qommons.Transaction;
import org.qommons.collect.TransactableMap;

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
					observer.onNext(createInitialEvent(value, null));
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

	/** @return The type of elements in the entry set. Should be cached, as type tokens aren't cheap to build. */
	TypeToken<ObservableEntry<K, V>> getEntryType();

	/**
	 * Builds an {@link #getEntryType() entry type} from the key and value types
	 *
	 * @param keyType The key type of the map
	 * @param valueType The value type of the map
	 * @return The entry type for the map
	 */
	static <K, V> TypeToken<ObservableEntry<K, V>> buildEntryType(TypeToken<K> keyType, TypeToken<V> valueType) {
		return new TypeToken<ObservableEntry<K, V>>() {}.where(new TypeParameter<K>() {}, keyType).where(new TypeParameter<V>() {},
			valueType);
	}

	@Override
	abstract boolean isLockSupported();

	/** @return The {@link Equivalence} that is used by this map's values (for {@link #containsValue(Object)}) */
	Equivalence<? super V> equivalence();

	@Override
	ObservableSet<K> keySet();

	/**
	 * @param key The key to get the value for
	 * @return An observable value that changes whenever the value for the given key changes in this map
	 */
	ObservableValue<V> observe(Object key);

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
	default ObservableSet<ObservableEntry<K, V>> observeEntries() {
		return keySet().flow().mapEquivalent(getEntryType()).cache(false).map(this::entryFor, entry -> entry.getKey()).collectLW();
	}

	/** @return An observable collection of all the values stored in this map */
	@Override
	default ObservableCollection<V> values() {
		return keySet().flow().flatMap(getValueType(), k -> observe(k)).collect();
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
		return observe(key).get();
	}

	@Override
	default ObservableSet<Entry<K, V>> entrySet() {
		return (ObservableSet<Entry<K, V>>) (ObservableSet<?>) observeEntries();
	}

	@Override
	default V put(K key, V value) {
		ObservableEntry<K, V> entry = entryFor(key);
		if (entry instanceof SettableValue)
			return ((SettableValue<V>) entry).set(value, null);
		else
			throw new UnsupportedOperationException();
	}

	@Override
	default void putAll(Map<? extends K, ? extends V> m) {
		for (Entry<? extends K, ? extends V> entry : m.entrySet())
			put(entry.getKey(), entry.getValue());
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

	/**
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 * @param keyType The key type for the map
	 * @param valueType The value type for the map
	 * @return An immutable, empty map with the given types
	 */
	static <K, V> ObservableMap<K, V> empty(TypeToken<K> keyType, TypeToken<V> valueType) {
		TypeToken<ObservableEntry<K, V>> entryType = buildEntryType(keyType, valueType);
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
			public TypeToken<ObservableEntry<K, V>> getEntryType() {
				return entryType;
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
				return ObservableCollection.constant(keyType).unique(false).collect();
			}

			@Override
			public ObservableValue<V> observe(Object key) {
				return ObservableValue.constant(valueType, null);
			}

			@Override
			public String toString() {
				return entrySet().toString();
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
