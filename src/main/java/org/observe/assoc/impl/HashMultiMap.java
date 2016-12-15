package org.observe.assoc.impl;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.observe.assoc.MultiMap;
import org.qommons.Equalizer;
import org.qommons.Equalizer.EqualizerNode;
import org.qommons.IterableUtils;

/**
 * A simple unobservable implementation of MultiMap
 *
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public class HashMultiMap<K, V> implements MultiMap<K, V> {
	private final Equalizer theEqualizer;
	private final Map<EqualizerNode<K>, Collection<V>> theMap;
	private final Supplier<? extends Collection<V>> theCollectionCreator;

	private int theSize;

	/** @param equalizer The equalizer to determining uniqueness in this map */
	public HashMultiMap(Equalizer equalizer) {
		this(equalizer, false, null);
	}

	/**
	 * @param equalizer The equalizer to determining uniqueness in this map
	 * @param concurrent Whether the map should handle multi-thread access
	 * @param collectCreator Creates collections for this map. May be null to use a default.
	 */
	public HashMultiMap(Equalizer equalizer, boolean concurrent, Supplier<? extends Collection<V>> collectCreator) {
		theEqualizer = equalizer;
		if(collectCreator == null) {
			collectCreator = java.util.ArrayList::new;
		}
		theCollectionCreator = collectCreator;
		theMap = concurrent ? new java.util.concurrent.ConcurrentHashMap<>() : new java.util.HashMap<>();
	}

	@Override
	public Set<K> keySet() {
		return new AbstractSet<K>() {
			private final Set<EqualizerNode<K>> keySet = theMap.keySet();

			@Override
			public int size() {
				return keySet.size();
			}

			@Override
			public boolean isEmpty() {
				return keySet.isEmpty();
			}

			@Override
			public boolean contains(Object o) {
				return keySet.contains(o);
			}

			@Override
			public Iterator<K> iterator() {
				return IterableUtils.map(keySet, EqualizerNode::get).iterator();
			}

			@Override
			public boolean add(K e) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean remove(Object o) {
				return keySet.remove(o);
			}

			@Override
			public boolean containsAll(Collection<?> c) {
				return keySet.containsAll(c);
			}

			@Override
			public boolean addAll(Collection<? extends K> c) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean retainAll(Collection<?> c) {
				return keySet.retainAll(c);
			}

			@Override
			public boolean removeAll(Collection<?> c) {
				return keySet.removeAll(c);
			}

			@Override
			public void clear() {
				keySet.clear();
			}
		};
	}

	@Override
	public Collection<V> values() {
		return new AbstractCollection<V>() {
			@Override
			public Iterator<V> iterator() {
				return new Iterator<V>() {
					private final Iterator<Collection<V>> backingOuter = theMap.values().iterator();

					private Iterator<V> backingInner;

					@Override
					public boolean hasNext() {
						while((backingInner == null || !backingInner.hasNext()) && backingOuter.hasNext()) {
							backingInner = backingOuter.next().iterator();
						}
						return backingInner != null && backingInner.hasNext();
					}

					@Override
					public V next() {
						if(!hasNext())
							throw new java.util.NoSuchElementException();
						return backingInner.next();
					}

					@Override
					public void remove() {
						backingInner.remove();
					}
				};
			}

			@Override
			public int size() {
				return theSize;
			}
		};
	}

	@Override
	public Set<? extends MultiEntry<K, V>> entrySet() {
		return new AbstractSet<MultiEntry<K, V>>() {
			@Override
			public Iterator<MultiEntry<K, V>> iterator() {
				return new Iterator<MultiEntry<K, V>>() {
					private final Iterator<Map.Entry<EqualizerNode<K>, Collection<V>>> backing = theMap.entrySet().iterator();

					@Override
					public boolean hasNext() {
						return backing.hasNext();
					}

					@Override
					public MultiEntry<K, V> next() {
						class MultEntryImpl extends AbstractCollection<V>implements MultiEntry<K, V> {
							private Map.Entry<EqualizerNode<K>, Collection<V>> backingEntry = backing.next();

							@Override
							public K getKey() {
								return backingEntry.getKey().get();
							}

							@Override
							public Iterator<V> iterator() {
								return backingEntry.getValue().iterator();
							}

							@Override
							public int size() {
								return backingEntry.getValue().size();
							}
						}
						return new MultEntryImpl();
					}

					@Override
					public void remove() {
						backing.remove();
					}
				};
			}

			@Override
			public int size() {
				return theMap.size();
			}
		};
	}

	@Override
	public Collection<V> get(Object key) {
		Collection<V> ret = theMap.get(key);
		if(ret == null)
			ret = theCollectionCreator.get();
		return ret;
	}

	private Collection<V> getOrCreateValues(K key) {
		Collection<V> coll = theMap.get(key);
		if(coll == null) {
			coll = theCollectionCreator.get();
			theMap.put(new EqualizerNode<>(theEqualizer, key), coll);
		}
		return coll;
	}

	@Override
	public boolean add(K key, V value) {
		Collection<V> v = getOrCreateValues(key);
		return v.add(value);
	}

	@Override
	public boolean addAll(K key, Collection<? extends V> values) {
		Collection<V> v = getOrCreateValues(key);
		return v.addAll(values);
	}

	@Override
	public boolean remove(K key, Object value) {
		Collection<V> v = theMap.get(key);
		if(v == null)
			return false;
		if(v.remove(value)) {
			if(v.isEmpty())
				theMap.remove(key);
			return true;
		} else
			return false;
	}

	@Override
	public boolean removeAll(K key) {
		Collection<V> v = theMap.remove(key);
		return v != null && !v.isEmpty();
	}
}
