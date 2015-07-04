package org.observe.datastruct;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableList;
import org.observe.collect.ObservableOrderedCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;
import org.observe.collect.OrderedObservableElement;
import org.observe.datastruct.ObservableMap.ObsEntryImpl;
import org.observe.util.Transaction;

import prisms.lang.Type;

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
	public interface ObservableMultiEntry<K, V> extends ObservableCollection<V> {
		/** @return The key associated with this entry's values */
		K getKey();
	}

	/** @return The type of keys this map uses */
	Type getKeyType();

	/** @return The type of values this map stores */
	Type getValueType();

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
	 * @return A multi-entry that represents the given key's presence in this map. Never null.
	 */
	default ObservableMultiEntry<K, V> entryFor(K key) {
		ObservableCollection<V> values = get(key);
		if(values instanceof ObservableList)
			return new ObsMultiEntryList<>(key, (ObservableList<V>) values);
		else if(values instanceof ObservableSortedSet)
			return new ObsMultiEntrySortedSet<>(key, (ObservableSortedSet<V>) values);
		else if(values instanceof ObservableOrderedCollection)
			return new ObsMultiEntryOrdered<>(key, (ObservableOrderedCollection<V>) values);
		else if(values instanceof ObservableSet)
			return new ObsMultiEntrySet<>(key, (ObservableSet<V>) values);
		else
			return new ObsMultiEntryImpl<>(key, values);
	}

	/** @return An observable collection of {@link ObservableMultiEntry observable entries} of all the key-value set pairs stored in this map */
	default ObservableSet<? extends ObservableMultiEntry<K, V>> observeEntries() {
		return ObservableSet.unique(keySet().map(this::entryFor));
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
	default ObservableMultiEntry<K, V> subscribe(K key) {
		ObservableValue<? extends ObservableMultiEntry<K, V>> existingEntry = observeEntries().find(
			entry -> java.util.Objects.equals(entry.getKey(), key));
		class WrappingMultiEntry implements ObservableCollection.PartialCollectionImpl<V>, ObservableMultiEntry<K, V> {
			@Override
			public Type getType() {
				return getValueType();
			}

			@Override
			public Subscription onElement(Consumer<? super ObservableElement<V>> onElement) {
				Subscription [] innerSub = new Subscription[1];
				Subscription outerSub = existingEntry.value().act(entry -> {
					Subscription is = innerSub[0];
					innerSub[0] = null;
					if(is != null)
						is.unsubscribe();
					if(entry != null)
						innerSub[0] = entry.onElement(onElement);
				});
				return () -> {
					outerSub.unsubscribe();
					Subscription is = innerSub[0];
					innerSub[0] = null;
					if(is != null)
						is.unsubscribe();
				};
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return ObservableValue.flatten(new Type(CollectionSession.class), existingEntry.mapV(ObservableCollection::getSession));
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return ObservableMultiMap.this.lock(write, cause);
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
	default ObservableMap<K, Collection<V>> asCollectionMap() {
		ObservableMultiMap<K, V> outer = this;
		class CollectionMap implements ObservableMap<K, Collection<V>> {
			@Override
			public Type getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public Type getValueType() {
				return new Type(ObservableCollection.class, outer.getValueType());
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
			public ObservableSet<K> keySet() {
				return outer.keySet();
			}

			@Override
			public ObservableValue<Collection<V>> observe(Object key) {
				return outer.get(key).asValue();
			}
		}
		return new CollectionMap();
	}

	/** @return An observable map with the same key set as this map and whose values are one of the elements in this multi-map for each key */
	default ObservableMap<K, V> unique() {
		ObservableMultiMap<K, V> outer = this;
		class UniqueMap implements ObservableMap<K, V> {
			@Override
			public Type getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public Type getValueType() {
				return new Type(ObservableCollection.class, outer.getValueType());
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
			public ObservableSet<K> keySet() {
				return outer.keySet();
			}

			@Override
			public ObservableValue<V> observe(Object key) {
				return outer.get(key).find(value -> true);
			}
		}
		return new UniqueMap();
	}

	/**
	 * @return An observable that fires a (null) value whenever anything in this structure changes. This observable will only fire 1 event
	 *         per transaction.
	 */
	default Observable<Void> changes() {
		return keySet().refreshEach(key -> get(key).simpleChanges()).simpleChanges();
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
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return outer.lock(write, cause);
			}

			@Override
			public ObservableSet<K> keySet() {
				return outer.keySet().immutable();
			}

			@Override
			public ObservableCollection<V> get(Object key) {
				return outer.get(key).immutable();
			}
		};
	}

	/**
	 * Simple multi-entry implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsMultiEntryImpl<K, V> implements ObservableMultiEntry<K, V> {
		private final K theKey;

		private final ObservableCollection<V> theValues;

		ObsMultiEntryImpl(K key, ObservableCollection<V> values) {
			theKey = key;
			theValues = values;
		}

		protected ObservableCollection<V> getWrapped() {
			return theValues;
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public Type getType() {
			return theValues.getType();
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<V>> onElement) {
			return theValues.onElement(onElement);
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theValues.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theValues.lock(write, cause);
		}

		@Override
		public int size() {
			return theValues.size();
		}

		@Override
		public Iterator<V> iterator() {
			return theValues.iterator();
		}

		@Override
		public boolean add(V e) {
			return theValues.add(e);
		}

		@Override
		public boolean remove(Object o) {
			return theValues.remove(o);
		}

		@Override
		public boolean addAll(Collection<? extends V> c) {
			return theValues.addAll(c);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return theValues.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return theValues.retainAll(c);
		}

		@Override
		public void clear() {
			theValues.clear();
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(theKey);
		}

		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			return obj instanceof ObsEntryImpl && Objects.equals(theKey, ((ObsMultiEntryImpl<?, ?>) obj).theKey);
		}
	}

	/**
	 * Simple ordered multi-entry implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsMultiEntryOrdered<K, V> extends ObsMultiEntryImpl<K, V> implements ObservableOrderedCollection<V> {
		public ObsMultiEntryOrdered(K key, ObservableOrderedCollection<V> values) {
			super(key, values);
		}

		@Override
		protected ObservableOrderedCollection<V> getWrapped() {
			return (ObservableOrderedCollection<V>) super.getWrapped();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super OrderedObservableElement<V>> onElement) {
			return getWrapped().onOrderedElement(onElement);
		}
	}

	/**
	 * Simple multi-entry sorted set implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsMultiEntrySortedSet<K, V> extends ObsMultiEntryOrdered<K, V> implements ObservableSortedSet<V> {
		public ObsMultiEntrySortedSet(K key, ObservableSortedSet<V> values) {
			super(key, values);
		}

		@Override
		protected ObservableSortedSet<V> getWrapped() {
			return (ObservableSortedSet<V>) super.getWrapped();
		}

		@Override
		public Iterable<V> descending() {
			return getWrapped().descending();
		}

		@Override
		public V pollFirst() {
			return getWrapped().pollFirst();
		}

		@Override
		public V pollLast() {
			return getWrapped().pollLast();
		}

		@Override
		public ObservableSortedSet<V> descendingSet() {
			return getWrapped().descendingSet();
		}

		@Override
		public Iterator<V> descendingIterator() {
			return getWrapped().descendingIterator();
		}

		@Override
		public ObservableSortedSet<V> subSet(V fromElement, boolean fromInclusive, V toElement, boolean toInclusive) {
			return getWrapped().subSet(fromElement, fromInclusive, toElement, toInclusive);
		}

		@Override
		public ObservableSortedSet<V> headSet(V toElement, boolean inclusive) {
			return getWrapped().headSet(toElement, inclusive);
		}

		@Override
		public ObservableSortedSet<V> tailSet(V fromElement, boolean inclusive) {
			return getWrapped().tailSet(fromElement, inclusive);
		}

		@Override
		public Comparator<? super V> comparator() {
			return getWrapped().comparator();
		}

		@Override
		public V first() {
			return getWrapped().first();
		}

		@Override
		public V last() {
			return getWrapped().last();
		}
	}

	/**
	 * Simple multi-entry list implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsMultiEntryList<K, V> extends ObsMultiEntryOrdered<K, V> implements ObservableList<V> {
		public ObsMultiEntryList(K key, ObservableList<V> values) {
			super(key, values);
		}

		@Override
		protected ObservableList<V> getWrapped() {
			return (ObservableList<V>) super.getWrapped();
		}

		@Override
		public boolean addAll(int index, Collection<? extends V> c) {
			return getWrapped().addAll(index, c);
		}

		@Override
		public V get(int index) {
			return getWrapped().get(index);
		}

		@Override
		public V set(int index, V element) {
			return getWrapped().set(index, element);
		}

		@Override
		public void add(int index, V element) {
			getWrapped().add(index, element);
		}

		@Override
		public V remove(int index) {
			return getWrapped().remove(index);
		}

		@Override
		public int indexOf(Object o) {
			return getWrapped().indexOf(o);
		}

		@Override
		public int lastIndexOf(Object o) {
			return getWrapped().lastIndexOf(o);
		}
	}

	/**
	 * Simple multi-entry set implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsMultiEntrySet<K, V> extends ObsMultiEntryImpl<K, V> implements ObservableSet<V> {
		public ObsMultiEntrySet(K key, ObservableSet<V> values) {
			super(key, values);
		}

		@Override
		protected ObservableSet<V> getWrapped() {
			return (ObservableSet<V>) super.getWrapped();
		}
	}
}
