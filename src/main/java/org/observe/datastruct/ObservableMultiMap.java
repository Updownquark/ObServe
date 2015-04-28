package org.observe.datastruct;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableSet;

import prisms.lang.Type;

/**
 * An observable map structure that allows more than one value to be stored per key
 *
 * @param <K> The type of key used by this map
 * @param <V> The type of values stored in this map
 */
public interface ObservableMultiMap<K, V> {
	/**
	 * A {@link java.util.Map.Entry} with observable capabilities
	 *
	 * @param <K> The type of key this entry uses
	 * @param <V> The type of value this entry stores
	 */
	public interface ObservableMultiEntry<K, V> extends ObservableCollection<V> {
		/** @return The key associated with this entry's values */
		K getKey();
	}

	/** @return The type of keys this map uses */
	Type getKeyType();

	/** @return The type of values this map stores */
	Type getValueType();

	/** @return An observable collection of {@link ObservableMultiEntry observable entries} of all the key-value set pairs stored in this map */
	ObservableCollection<ObservableMultiEntry<K, V>> observeEntries();

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
	 * @param key The key to store the value by
	 * @param value The value to store
	 * @return Whether the map was changed as a result
	 */
	boolean add(K key, V value);

	/**
	 * @param key The key to store the value by
	 * @param values The values to store
	 * @return Whether the map was changed as a result
	 */
	boolean addAll(K key, Collection<? extends V> values);

	/**
	 * @param key The key that the value may be stored by
	 * @param value The value to remove
	 * @return Whether the map was changed as a result
	 */
	boolean remove(K key, Object value);

	/**
	 * @param key The key to remove all values from
	 * @return Whether the map was changed as a result
	 */
	boolean removeAll(K key);

	/** @return All keys stored in this map */
	default ObservableSet<K> observeKeys() {
		return ObservableSet.unique(observeEntries().map(getKeyType(), ObservableMultiEntry<K, V>::getKey));
	}

	/** @return All values stored in this map */
	default ObservableCollection<V> observeValues() {
		return ObservableCollection.flatten(observeEntries());
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
		}
		return ObservableCollection.flatten(observeEntries().map(entry -> entry.map(value -> new DefaultMapEntry(entry.getKey(), value))));
	}

	/**
	 * @param key The key to get the values for
	 * @return The values (in the form of a {@link ObservableMultiEntry multi-entry}) stored for the given key
	 */
	default ObservableMultiEntry<K, V> observe(K key) {
		ObservableValue<ObservableMultiEntry<K, V>> existingEntry = observeEntries().find(
			entry -> java.util.Objects.equals(entry.getKey(), key));
		class WrappingMultiEntry extends java.util.AbstractCollection<V> implements ObservableMultiEntry<K, V> {
			@Override
			public Type getType() {
				return getValueType();
			}

			@Override
			public Runnable onElement(Consumer<? super ObservableElement<V>> onElement) {
				Runnable [] innerSub = new Runnable[1];
				Runnable outerSub = existingEntry.value().act(entry -> {
					Runnable is = innerSub[0];
					innerSub[0] = null;
					if(is != null)
						is.run();
					if(entry != null)
						innerSub[0] = entry.onElement(onElement);
				});
				return () -> {
					outerSub.run();
					Runnable is = innerSub[0];
					innerSub[0] = null;
					if(is != null)
						is.run();
				};
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return ObservableValue.flatten(new Type(CollectionSession.class), existingEntry.mapV(ObservableCollection::getSession));
			}

			@Override
			public K getKey() {
				return key;
			}

			@Override
			public Iterator<V> iterator() {
				ObservableMultiEntry<K, V> ee = existingEntry.get();
				return ee == null ? java.util.Collections.EMPTY_LIST.iterator() : ee.iterator();
			}

			@Override
			public int size() {
				ObservableMultiEntry<K, V> ee = existingEntry.get();
				return ee == null ? 0 : ee.size();
			}

			@Override
			public boolean add(V e) {
				return ObservableMultiMap.this.add(key, e);
			}

			@Override
			public boolean remove(Object o) {
				return ObservableMultiMap.this.remove(key, o);
			}

			@Override
			public void clear() {
				ObservableMultiMap.this.removeAll(key);
			}
		}
		return new WrappingMultiEntry();
	}

	/** @return An observable map of collections which mirrors the keys and values in this multi-map */
	default ObservableMap<K, ObservableCollection<V>> asCollectionMap() {
	}

	/** @return An immutable copy of this map */
	default ObservableMultiMap<K, V> immutable() {
		ObservableMultiMap<K, V> outer = this;
		return new ObservableMultiMap<K, V>() {
			@Override
			public Type getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public Type getValueType() {
				return outer.getValueType();
			}

			@Override
			public ObservableCollection<ObservableMultiEntry<K, V>> observeEntries() {
				return outer.observeEntries().immutable();
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
			}

			@Override
			public boolean add(K key, V value) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean addAll(K key, Collection<? extends V> values) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean remove(K key, Object value) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean removeAll(K key) {
				throw new UnsupportedOperationException();
			}
		};
	}
}
