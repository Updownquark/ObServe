package org.observe.datastruct;

import java.util.Map;
import java.util.Objects;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.util.ObservableUtils;

import prisms.lang.Type;

/**
 * A map with observable capabilities
 *
 * @param <K> The type of keys this map uses
 * @param <V> The type of values this map stores
 */
public interface ObservableMap<K, V> extends Map<K, V> {
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
	}

	/** @return The type of keys this map uses */
	Type getKeyType();

	/** @return The type of values this map stores */
	Type getValueType();

	@Override
	ObservableSet<K> keySet();

	/**
	 * @param key The key to get the value for
	 * @return An observable value that changes whenever the value for the given key changes in this map
	 */
	ObservableValue<V> observe(Object key);

	/**
	 * @return The observable value for the current session of this map. The session allows listeners to retain state for the duration of a
	 *         unit of work (controlled by implementation-specific means), batching events where possible. Not all events on a map will have
	 *         a session (the value may be null). In addition, the presence or absence of a session need not imply anything about the
	 *         threaded interactions with a session. A transaction may encompass events fired and received on multiple threads. In short,
	 *         the only thing guaranteed about sessions is that they will end. Therefore, if a session is present, observers may assume that
	 *         they can delay expensive results of map events until the session completes. The {@link ObservableCollection#getSession()
	 *         sessions} of the {@link #observeEntries() entries}, {@link #observeKeys() keys}, and {@link #observeValues() values}
	 *         collections should be the same as this one.
	 */
	ObservableValue<CollectionSession> getSession();

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
		return ObservableSet.unique(keySet().map(this::entryFor));
	}

	/** @return An observable value reflecting the number of key-value pairs stored in this map */
	default ObservableValue<Integer> observeSize() {
		return keySet().observeSize();
	}

	/** @return An observable collection of all the values stored in this map */
	@Override
	default ObservableCollection<V> values() {
		Type obValType = new Type(ObservableValue.class, getValueType());
		return ObservableUtils.flattenValues(getValueType(), keySet().map(obValType, this::observe));
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
	default Observable<Void> changes() {
		return observeEntries().simpleChanges();
	}

	/** @return An immutable copy of this map */
	default ObservableMap<K, V> immutable() {
		ObservableMap<K, V> outer = this;
		class Immutable implements ObservableMap<K, V> {
			@Override
			public Type getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public Type getValueType() {
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
			public ObservableSet<ObservableEntry<K, V>> observeEntries() {
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
		}
		return new Immutable();
	}

	/**
	 * @param keyType The key type for the map
	 * @param valueType The value type for the map
	 * @return An immutable, empty map with the given types
	 */
	static <K, V> ObservableMap<K, V> empty(Type keyType, Type valueType) {
		return new ObservableMap<K, V>() {
			@Override
			public Type getKeyType() {
				return keyType;
			}

			@Override
			public Type getValueType() {
				return valueType;
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
			public ObservableValue<CollectionSession> getSession() {
				return ObservableValue.constant(new Type(CollectionSession.class), null);
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
		public Type getType() {
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
		public ObservableValue<Boolean> isEnabled() {
			return getWrapped().isEnabled();
		}
	}
}
