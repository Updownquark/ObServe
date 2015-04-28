package org.observe.datastruct;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.observe.ObservableValue;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;

import prisms.lang.Type;

/**
 * A map with observable capabilities
 *
 * @param <K> The type of keys this map uses
 * @param <V> The type of values this map stores
 */
public interface ObservableMap<K, V> extends Map<K, V> {
	/**
	 * A {@link java.util.Map.Entry} with observable capabilities
	 *
	 * @param <K> The type of key this entry uses
	 * @param <V> The type of value this entry stores
	 */
	public interface ObservableEntry<K, V> extends Map.Entry<K, V>, ObservableValue<V> {
		@Override
		default V get() {
			return getValue();
		}
	}

	/** @return The type of keys this map uses */
	Type getKeyType();

	/** @return The type of values this map stores */
	Type getValueType();

	/** @return An observable collection of {@link ObservableEntry observable entries} of all the key-value pairs stored in this map */
	ObservableCollection<ObservableEntry<K, V>> observeEntries();

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
	 * @param key The key to observe the value of
	 * @return An observable value whose value is the same as {@link #get(Object)} for the given key, but updates as the map is changed
	 */
	default ObservableValue<V> observe(K key) {
		return observeEntries().find(entry -> java.util.Objects.equals(entry.getKey(), key)).mapV(Map.Entry<K, V>::getValue);
	}

	/** @return An observable value reflecting the number of key-value pairs stored in this map */
	default ObservableValue<Integer> observeSize() {
		return observeEntries().observeSize();
	}

	/** @return An observable set of all keys stored in this map */
	default ObservableSet<K> observeKeys() {
		return ObservableSet.unique(observeEntries().map(getKeyType(), Map.Entry<K, V>::getKey));
	}

	/** @return An observable collection of all the values stored in this map */
	default ObservableCollection<V> observeValues() {
		return observeEntries().map(getValueType(), Map.Entry<K, V>::getValue);
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
		return observeEntries().find(entry -> java.util.Objects.equals(entry.getKey(), key)).mapV(Map.Entry<K, V>::getValue).get();
	}

	@Override
	default Set<K> keySet() {
		return observeKeys();
	}

	@Override
	default Collection<V> values() {
		return observeValues();
	}

	@Override
	default Set<Map.Entry<K, V>> entrySet() {
		return (Set<Map.Entry<K, V>>) (Set<?>) observeEntries();
	}

	/** @return An immutable copy of this map */
	default ObservableMap<K, V> immutable() {
		ObservableMap<K, V> outer = this;
		class Immutable extends java.util.AbstractMap<K, V> implements ObservableMap<K, V> {
			@Override
			public Type getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public Type getValueType() {
				return outer.getValueType();
			}

			@Override
			public ObservableCollection<ObservableEntry<K, V>> observeEntries() {
				return outer.observeEntries().immutable();
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
			}

			@Override
			public Set<java.util.Map.Entry<K, V>> entrySet() {
				return outer.entrySet();
			}
		}
		return new Immutable();
	}
}
