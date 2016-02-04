package org.observe.datastruct;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableList;
import org.observe.collect.ObservableOrderedCollection;
import org.observe.collect.ObservableOrderedElement;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;
import org.observe.datastruct.ObservableMap.ObsEntryImpl;
import org.qommons.Transaction;

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
	public interface ObservableMultiEntry<K, V> extends MultiEntry<K, V>, ObservableCollection<V> {
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
	 *         sessions} of the {@link #entrySet() entries}, {@link #observeKeys() keys}, and {@link #entrySet() values} collections should
	 *         be the same as this one.
	 */
	ObservableValue<CollectionSession> getSession();

	/** @return The keys that have least one value in this map */
	@Override
	ObservableSet<K> keySet();

	/**
	 * <p>
	 * A default implementation of {@link #keySet()}.
	 * </p>
	 * <p>
	 * No {@link ObservableMultiMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #entrySet()} methods. {@link #defaultEntrySet(ObservableMultiMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(ObservableMultiMap)} or {@link #defaultGet(ObservableMultiMap, Object)}. Either {@link #entrySet()} or both
	 * {@link #keySet()} and {@link #get(Object)} must be custom. If an implementation supplies custom {@link #keySet()} and
	 * {@link #get(Object)} implementations, it may use {@link #defaultEntrySet(ObservableMultiMap)} for its {@link #entrySet()} . If
	 * an implementation supplies a custom {@link #entrySet()} implementation, it may use {@link #defaultKeySet(ObservableMultiMap)} and
	 * {@link #defaultGet(ObservableMultiMap, Object)} for its {@link #keySet()} and {@link #get(Object)} implementations, respectively.
	 * Using default implementations for both will result in infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create a key set for
	 * @return A key set for the map
	 */
	public static <K, V> ObservableSet<K> defaultKeySet(ObservableMultiMap<K, V> map) {
		return ObservableSet.unique(map.entrySet().map(ObservableMultiEntry::getKey));
	}

	/**
	 * @param key The key to get values for
	 * @return The collection of values stored for the given key in this map. Never null.
	 */
	@Override
	ObservableCollection<V> get(Object key);

	/**
	 * <p>
	 * A default implementation of {@link #get(Object)}.
	 * </p>
	 * <p>
	 * No {@link ObservableMultiMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #entrySet()} methods. {@link #defaultEntrySet(ObservableMultiMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(ObservableMultiMap)} or {@link #defaultGet(ObservableMultiMap, Object)}. Either {@link #entrySet()} or both
	 * {@link #keySet()} and {@link #get(Object)} must be custom. If an implementation supplies custom {@link #keySet()} and
	 * {@link #get(Object)} implementations, it may use {@link #defaultEntrySet(ObservableMultiMap)} for its {@link #entrySet()} . If
	 * an implementation supplies a custom {@link #entrySet()} implementation, it may use {@link #defaultKeySet(ObservableMultiMap)} and
	 * {@link #defaultGet(ObservableMultiMap, Object)} for its {@link #keySet()} and {@link #get(Object)} implementations, respectively.
	 * Using default implementations for both will result in infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create an entry set for
	 * @param key The key to get the value collection for
	 * @return A key set for the map
	 */
	public static <K, V> ObservableCollection<V> defaultGet(ObservableMultiMap<K, V> map, Object key) {
		if(key != null && !map.getKeyType().isAssignableFrom(key.getClass()))
			return ObservableList.constant(map.getValueType());

		ObservableSet<? extends ObservableMultiEntry<K, V>> entries = map.entrySet();
		Map.Entry<Object, ?> keyEntry = new Map.Entry<Object, Object>() {
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
		};

		ObservableValue<? extends ObservableMultiEntry<K, V>> equiv = entries.equivalent(keyEntry);
		if(equiv.getType().isAssignableFrom(ObservableList.class)) {
			return new ObsMultiEntryList<>(map, (K) key, map.getValueType(), (ObservableValue<? extends ObservableList<V>>) equiv);
		} else if(equiv.getType().isAssignableFrom(ObservableSortedSet.class)) {
			return new ObsMultiEntrySortedSet<>(map, (K) key, map.getValueType(),
				(ObservableValue<? extends ObservableSortedSet<V>>) equiv);
		} else if(equiv.getType().isAssignableFrom(ObservableOrderedCollection.class)) {
			return new ObsMultiEntryOrdered<>(map, (K) key, map.getValueType(),
				(ObservableValue<? extends ObservableOrderedCollection<V>>) equiv);
		} else if(equiv.getType().isAssignableFrom(ObservableSet.class)) {
			return new ObsMultiEntrySet<>(map, (K) key, map.getValueType(), (ObservableValue<? extends ObservableSet<V>>) equiv);
		} else {
			return new ObsMultiEntryImpl<>(map, (K) key, map.getValueType(), equiv);
		}
	}

	/**
	 * @param key The key to get the entry for
	 * @return A multi-entry that represents the given key's presence in this map. Never null.
	 */
	default ObservableMultiEntry<K, V> entryFor(K key) {
		ObservableCollection<V> values = get(key);
		if(values instanceof ObservableMultiEntry)
			return (ObservableMultiEntry<K, V>) values;
		if(values instanceof ObservableList)
			return new ObsMultiEntryList<>(this, key, (ObservableList<V>) values);
		else if(values instanceof ObservableSortedSet)
			return new ObsMultiEntrySortedSet<>(this, key, (ObservableSortedSet<V>) values);
		else if(values instanceof ObservableOrderedCollection)
			return new ObsMultiEntryOrdered<>(this, key, (ObservableOrderedCollection<V>) values);
		else if(values instanceof ObservableSet)
			return new ObsMultiEntrySet<>(this, key, (ObservableSet<V>) values);
		else
			return new ObsMultiEntryImpl<>(this, key, values);
	}

	/** @return An observable collection of {@link ObservableMultiEntry observable entries} of all the key-value set pairs stored in this map */
	@Override
	ObservableSet<? extends ObservableMultiEntry<K, V>> entrySet();

	/**
	 * <p>
	 * A default implementation of {@link #entrySet()}.
	 * </p>
	 * <p>
	 * No {@link ObservableMultiMap} implementation may use the default implementations for its {@link #keySet()}, {@link #get(Object)}, and
	 * {@link #entrySet()} methods. {@link #defaultEntrySet(ObservableMultiMap)} may not be used in the same implementation as
	 * {@link #defaultKeySet(ObservableMultiMap)} or {@link #defaultGet(ObservableMultiMap, Object)}. Either {@link #entrySet()} or both
	 * {@link #keySet()} and {@link #get(Object)} must be custom. If an implementation supplies custom {@link #keySet()} and
	 * {@link #get(Object)} implementations, it may use {@link #defaultEntrySet(ObservableMultiMap)} for its {@link #entrySet()} . If
	 * an implementation supplies a custom {@link #entrySet()} implementation, it may use {@link #defaultKeySet(ObservableMultiMap)} and
	 * {@link #defaultGet(ObservableMultiMap, Object)} for its {@link #keySet()} and {@link #get(Object)} implementations, respectively.
	 * Using default implementations for both will result in infinite loops.
	 * </p>
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param map The map to create an entry set for
	 * @return An entry set for the map
	 */
	public static <K, V> ObservableSet<? extends ObservableMultiEntry<K, V>> defaultEntrySet(ObservableMultiMap<K, V> map) {
		return ObservableSet.unique(map.keySet().map(map::entryFor));
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
		return ObservableSet.unique(entrySet().map(getKeyType(), ObservableMultiEntry<K, V>::getKey));
	}

	/** @return All values stored in this map */
	@Override
	default ObservableCollection<V> values() {
		return ObservableCollection.flatten(entrySet());
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
		return ObservableCollection.flatten(entrySet().map(entry -> entry.map(value -> new DefaultMapEntry(entry.getKey(), value))));
	}

	/**
	 * @param key The key to get the values for
	 * @return The values (in the form of a {@link ObservableMultiEntry multi-entry}) stored for the given key
	 */
	default ObservableMultiEntry<K, V> subscribe(K key) {
		ObservableValue<? extends ObservableMultiEntry<K, V>> existingEntry = entrySet()
			.find(
				entry -> java.util.Objects.equals(entry.getKey(), key));
		class WrappingMultiEntry implements ObservableCollection.PartialCollectionImpl<V>, ObservableMultiEntry<K, V> {
			@Override
			public TypeToken<V> getType() {
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
				return ObservableValue.flatten(TypeToken.of(CollectionSession.class), existingEntry.mapV(ObservableCollection::getSession));
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
			private TypeToken<Collection<V>> theValueType = new TypeToken<Collection<V>>() {}.where(new TypeParameter<V>() {},
				outer.getValueType());
			@Override
			public TypeToken<K> getKeyType() {
				return outer.getKeyType();
			}

			@Override
			public TypeToken<Collection<V>> getValueType() {
				return theValueType;
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

			@Override
			public ObservableSet<? extends ObservableEntry<K, Collection<V>>> observeEntries() {
				return ObservableMap.defaultObserveEntries(this);
			}
		}
		return new CollectionMap();
	}

	/** @return An observable map with the same key set as this map and whose values are one of the elements in this multi-map for each key */
	default ObservableMap<K, V> unique() {
		ObservableMultiMap<K, V> outer = this;
		class UniqueMap implements ObservableMap<K, V> {
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

			@Override
			public ObservableSet<? extends ObservableEntry<K, V>> observeEntries() {
				return ObservableMap.defaultObserveEntries(this);
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

	/**
	 * @param <T> The type of values to map to
	 * @param map The function to map values
	 * @return A map with the same key set, but with its values mapped according to the given mapping function
	 */
	default <T> ObservableMultiMap<K, T> map(Function<? super V, T> map) {
		ObservableMultiMap<K, V> outer = this;
		return new ObservableMultiMap<K, T>() {
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
			public ObservableCollection<T> get(Object key) {
				return outer.get(key).map(map);
			}

			@Override
			public ObservableSet<? extends ObservableMultiEntry<K, T>> entrySet() {
				return ObservableMultiMap.defaultEntrySet(this);
			}
		};
	}

	/** @return An immutable copy of this map */
	default ObservableMultiMap<K, V> immutable() {
		ObservableMultiMap<K, V> outer = this;
		return new ObservableMultiMap<K, V>() {
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

			@Override
			public ObservableSet<? extends ObservableMultiEntry<K, V>> entrySet() {
				return outer.entrySet().immutable();
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
		private final ObservableMultiMap<K, V> theMap;
		private final K theKey;

		private final TypeToken<V> theValueType;

		private final ObservableValue<? extends ObservableCollection<V>> theValues;

		ObsMultiEntryImpl(ObservableMultiMap<K, V> map, K key, ObservableCollection<V> values) {
			this(map, key, values.getType(), ObservableValue
				.constant(new TypeToken<ObservableCollection<V>>() {}.where(new TypeParameter<V>() {}, values.getType()), values));
		}

		ObsMultiEntryImpl(ObservableMultiMap<K, V> map, K key, TypeToken<V> valueType,
			ObservableValue<? extends ObservableCollection<V>> values) {
			theMap = map;
			theKey = key;
			theValueType = valueType;
			theValues = values;
		}

		protected ObservableMultiMap<K, V> getMap() {
			return theMap;
		}

		protected ObservableCollection<V> getWrapped() {
			return theValues.get();
		}

		protected ObservableValue<? extends ObservableCollection<V>> getWrappedObservable() {
			return theValues;
		}

		@Override
		public K getKey() {
			return theKey;
		}

		@Override
		public TypeToken<V> getType() {
			return theValueType;
		}

		@Override
		public Subscription onElement(Consumer<? super ObservableElement<V>> onElement) {
			return theValues.subscribe(new Observer<ObservableValueEvent<? extends ObservableCollection<V>>>() {
				private Subscription theSubscription;

				@Override
				public <V2 extends ObservableValueEvent<? extends ObservableCollection<V>>> void onNext(V2 event) {
					if(theSubscription != null) {
						theSubscription.unsubscribe();
						theSubscription = null;
					}
					if(event.getValue() != null) {
						event.getValue().onElement(element -> {
							onElement.accept(element.takeUntil(theValues));
						});
					}
				}
			});
		}

		@Override
		public ObservableValue<CollectionSession> getSession() {
			return theMap.getSession();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return theMap.lock(write, cause);
		}

		@Override
		public int size() {
			ObservableCollection<V> current = getWrapped();
			return current != null ? current.size() : 0;
		}

		@Override
		public Iterator<V> iterator() {
			ObservableCollection<V> current = getWrapped();
			return current != null ? current.iterator() : new Iterator<V>() {
				@Override
				public boolean hasNext() {
					return false;
				}

				@Override
				public V next() {
					throw new java.util.NoSuchElementException();
				}
			};
		}

		@Override
		public boolean add(V e) {
			return theMap.add(theKey, e);
		}

		@Override
		public boolean addAll(Collection<? extends V> c) {
			return theMap.addAll(theKey, c);
		}

		@Override
		public boolean remove(Object o) {
			ObservableCollection<V> current = getWrapped();
			if(current == null)
				return false;
			return current.remove(o);
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			ObservableCollection<V> current = getWrapped();
			if(current == null)
				return false;
			return current.removeAll(c);
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			ObservableCollection<V> current = getWrapped();
			if(current == null)
				return false;
			return current.retainAll(c);
		}

		@Override
		public void clear() {
			ObservableCollection<V> current = getWrapped();
			if(current == null)
				return;
			current.clear();
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

		@Override
		public String toString() {
			return theKey + "=" + theValues.get();
		}
	}

	/**
	 * Simple ordered multi-entry implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsMultiEntryOrdered<K, V> extends ObsMultiEntryImpl<K, V> implements ObservableOrderedCollection<V> {
		public ObsMultiEntryOrdered(ObservableMultiMap<K, V> map, K key, ObservableOrderedCollection<V> values) {
			super(map, key, values);
		}

		public ObsMultiEntryOrdered(ObservableMultiMap<K, V> map, K key, TypeToken<V> valueType,
			ObservableValue<? extends ObservableOrderedCollection<V>> values) {
			super(map, key, valueType, values);
		}

		@Override
		protected ObservableOrderedCollection<V> getWrapped() {
			return (ObservableOrderedCollection<V>) super.getWrapped();
		}

		@Override
		protected ObservableValue<? extends ObservableOrderedCollection<V>> getWrappedObservable() {
			return (ObservableValue<? extends ObservableOrderedCollection<V>>) super.getWrappedObservable();
		}

		@Override
		public Subscription onOrderedElement(Consumer<? super ObservableOrderedElement<V>> onElement) {
			ObservableValue<? extends ObservableOrderedCollection<V>> values = getWrappedObservable();
			return values.subscribe(new Observer<ObservableValueEvent<? extends ObservableOrderedCollection<V>>>() {
				private Subscription theSubscription;

				@Override
				public <V2 extends ObservableValueEvent<? extends ObservableOrderedCollection<V>>> void onNext(V2 event) {
					if(theSubscription != null) {
						theSubscription.unsubscribe();
						theSubscription = null;
					}
					if(event.getValue() != null) {
						event.getValue().onOrderedElement(element -> {
							onElement.accept(element.takeUntil(values));
						});
					}
				}
			});
		}
	}

	/**
	 * Simple multi-entry sorted set implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsMultiEntrySortedSet<K, V> extends ObsMultiEntryOrdered<K, V> implements ObservableSortedSet<V> {
		public ObsMultiEntrySortedSet(ObservableMultiMap<K, V> map, K key, ObservableSortedSet<V> values) {
			super(map, key, values);
		}

		public ObsMultiEntrySortedSet(ObservableMultiMap<K, V> map, K key, TypeToken<V> valueType,
			ObservableValue<? extends ObservableSortedSet<V>> values) {
			super(map, key, valueType, values);
		}

		@Override
		protected ObservableSortedSet<V> getWrapped() {
			return (ObservableSortedSet<V>) super.getWrapped();
		}

		@Override
		public Iterable<V> descending() {
			return () -> {
				return descendingIterator();
			};
		}

		@Override
		public V pollFirst() {
			ObservableSortedSet<V> current = getWrapped();
			return current != null ? current.pollFirst() : null;
		}

		@Override
		public V pollLast() {
			ObservableSortedSet<V> current = getWrapped();
			return current != null ? current.pollLast() : null;
		}

		@Override
		public Iterator<V> descendingIterator() {
			ObservableSortedSet<V> current = getWrapped();
			return current != null ? current.descendingIterator() : new Iterator<V>() {
				@Override
				public boolean hasNext() {
					return false;
				}

				@Override
				public V next() {
					throw new java.util.NoSuchElementException();
				}
			};
		}

		@Override
		public Comparator<? super V> comparator() {
			ObservableSortedSet<V> current = getWrapped();
			return current != null ? current.comparator() : null;
		}

		@Override
		public V first() {
			ObservableSortedSet<V> current = getWrapped();
			if(current != null)
				return current.first();
			throw new java.util.NoSuchElementException();
		}

		@Override
		public V last() {
			ObservableSortedSet<V> current = getWrapped();
			if(current != null)
				return current.last();
			throw new java.util.NoSuchElementException();
		}
	}

	/**
	 * Simple multi-entry list implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsMultiEntryList<K, V> extends ObsMultiEntryOrdered<K, V> implements ObservableList<V> {
		public ObsMultiEntryList(ObservableMultiMap<K, V> map, K key, ObservableList<V> values) {
			super(map, key, values);
		}

		public ObsMultiEntryList(ObservableMultiMap<K, V> map, K key, TypeToken<V> valueType,
			ObservableValue<? extends ObservableList<V>> values) {
			super(map, key, valueType, values);
		}

		@Override
		protected ObservableList<V> getWrapped() {
			return (ObservableList<V>) super.getWrapped();
		}

		@Override
		public boolean addAll(int index, Collection<? extends V> c) {
			ObservableList<V> current = getWrapped();
			if(current == null) {
				if(index == 0)
					return getMap().addAll(getKey(), c);
				else
					throw new IndexOutOfBoundsException(index + " of 0");
			} else
				return current.addAll(index, c);
		}

		@Override
		public V get(int index) {
			ObservableList<V> current = getWrapped();
			if(current == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			else
				return current.get(index);
		}

		@Override
		public V set(int index, V element) {
			ObservableList<V> current = getWrapped();
			if(current == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			else
				return current.set(index, element);
		}

		@Override
		public void add(int index, V element) {
			ObservableList<V> current = getWrapped();
			if(current == null) {
				if(index == 0)
					getMap().add(getKey(), element);
				else
					throw new IndexOutOfBoundsException(index + " of 0");
			} else
				current.add(index, element);
		}

		@Override
		public V remove(int index) {
			ObservableList<V> current = getWrapped();
			if(current == null)
				throw new IndexOutOfBoundsException(index + " of 0");
			return current.remove(index);
		}

		@Override
		public int indexOf(Object o) {
			ObservableList<V> current = getWrapped();
			return current != null ? current.indexOf(o) : -1;
		}

		@Override
		public int lastIndexOf(Object o) {
			ObservableList<V> current = getWrapped();
			return current != null ? current.lastIndexOf(o) : -1;
		}
	}

	/**
	 * Simple multi-entry set implementation
	 *
	 * @param <K> The key type for this entry
	 * @param <V> The value type for this entry
	 */
	class ObsMultiEntrySet<K, V> extends ObsMultiEntryImpl<K, V> implements ObservableSet<V> {
		public ObsMultiEntrySet(ObservableMultiMap<K, V> map, K key, ObservableSet<V> values) {
			super(map, key, values);
		}

		public ObsMultiEntrySet(ObservableMultiMap<K, V> map, K key, TypeToken<V> valueType,
			ObservableValue<? extends ObservableSet<V>> values) {
			super(map, key, valueType, values);
		}

		@Override
		protected ObservableSet<V> getWrapped() {
			return (ObservableSet<V>) super.getWrapped();
		}
	}
}
