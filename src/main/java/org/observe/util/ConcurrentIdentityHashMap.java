package org.observe.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A thead-safe, object-identity-keyed map
 *
 * @param <K> The key type of the map
 * @param <V> The value type of the map
 */
public class ConcurrentIdentityHashMap<K, V> implements Map<K, V> {
	private static final Object NULL = new Object();

	private static final class IdentityWrapper<K> {
		final K wrapped;

		IdentityWrapper(K wrap) {
			wrapped = wrap;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(wrapped);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof IdentityWrapper && wrapped == ((IdentityWrapper<?>) obj).wrapped;
		}

		@Override
		public String toString() {
			return wrapped.toString();
		}
	}

	private final java.util.concurrent.ConcurrentHashMap<IdentityWrapper<K>, V> theBacking;

	/** Creates the map */
	public ConcurrentIdentityHashMap() {
		theBacking = new java.util.concurrent.ConcurrentHashMap<>();
	}

	@Override
	public int size() {
		return theBacking.size();
	}

	@Override
	public boolean isEmpty() {
		return theBacking.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return theBacking.containsKey(new IdentityWrapper<>(key));
	}

	@Override
	public boolean containsValue(Object value) {
		return theBacking.containsValue(value);
	}

	@Override
	public V get(Object key) {
		V ret = theBacking.get(new IdentityWrapper<>(key));
		if(ret == NULL)
			return null;
		return ret;
	}

	@Override
	public V put(K key, V value) {
		return theBacking.put(new IdentityWrapper<>(key), value);
	}

	@Override
	public V remove(Object key) {
		return theBacking.remove(new IdentityWrapper<>(key));
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		for(Map.Entry<? extends K, ? extends V> entry : m.entrySet())
			theBacking.put(new IdentityWrapper<>(entry.getKey()), entry.getValue());
	}

	@Override
	public void clear() {
		theBacking.clear();
	}

	@Override
	public Set<K> keySet() {
		return new java.util.AbstractSet<K>() {
			private final Set<IdentityWrapper<K>> backing = theBacking.keySet();

			@Override
			public Iterator<K> iterator() {
				return new Iterator<K>() {
					private final Iterator<IdentityWrapper<K>> backingIter = backing.iterator();

					@Override
					public boolean hasNext() {
						return backingIter.hasNext();
					}

					@Override
					public K next() {
						return backingIter.next().wrapped;
					}

					@Override
					public void remove() {
						backingIter.remove();
					}
				};
			}

			@Override
			public int size() {
				return backing.size();
			}

			@Override
			public boolean isEmpty() {
				return ConcurrentIdentityHashMap.this.isEmpty();
			}

			@Override
			public boolean contains(Object o) {
				return containsKey(o);
			}

			@Override
			public boolean add(K e) {
				return theBacking.put(new IdentityWrapper<>(e), (V) NULL) == null;
			}

			@Override
			public boolean remove(Object o) {
				return theBacking.remove(new IdentityWrapper<>(o)) != null;
			}

			@Override
			public void clear() {
				theBacking.clear();
			}
		};
	}

	@Override
	public Collection<V> values() {
		return theBacking.values();
	}

	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		return new java.util.AbstractSet<Map.Entry<K, V>>() {
			private final Set<Map.Entry<IdentityWrapper<K>, V>> backing = theBacking.entrySet();

			@Override
			public Iterator<Map.Entry<K, V>> iterator() {
				return new Iterator<Map.Entry<K, V>>() {
					private final Iterator<Map.Entry<IdentityWrapper<K>, V>> backingIter = backing.iterator();

					@Override
					public boolean hasNext() {
						return backingIter.hasNext();
					}

					@Override
					public Map.Entry<K, V> next() {
						return new Map.Entry<K, V>() {
							private Map.Entry<IdentityWrapper<K>, V> backingEntry = backingIter.next();

							@Override
							public K getKey() {
								return backingEntry.getKey().wrapped;
							}

							@Override
							public V getValue() {
								return backingEntry.getValue();
							}

							@Override
							public V setValue(V value) {
								return backingEntry.setValue(value);
							}
						};
					}

					@Override
					public void remove() {
						backingIter.remove();
					}
				};
			}

			@Override
			public int size() {
				return backing.size();
			}

			@Override
			public boolean isEmpty() {
				return ConcurrentIdentityHashMap.this.isEmpty();
			}

			@Override
			public void clear() {
				theBacking.clear();
			}
		};
	}

	@Override
	public String toString() {
		return theBacking.toString();
	}
}
