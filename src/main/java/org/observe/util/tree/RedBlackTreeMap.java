package org.observe.util.tree;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.SortedMap;

import org.observe.util.tree.RedBlackNode.TreeOpResult;
import org.observe.util.tree.RedBlackNode.ValuedRedBlackNode;

public interface RedBlackTreeMap<K, V, N extends ValuedRedBlackNode<Map.Entry<K, V>>> extends java.util.NavigableMap<K, V> {
	static final class DefaultEntry<K, V> implements Map.Entry<K, V> {
		private final K theKey;

		private V theValue;

		DefaultEntry(K key) {
			theKey = key;
		}

		DefaultEntry(K key, V value) {
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
			V ret = theValue;
			theValue = value;
			return ret;
		}
	}

	N getRoot();

	N createRoot(K key, V value);

	void setRoot(N root);

	default V getDefaultValue() {
		return null;
	}

	default Entry<K, V> keyEntry(K key) {
		return key == null ? null : new Map.Entry<K, V>() {
			@Override
			public K getKey() {
				return key;
			}

			@Override
			public V getValue() {
				return null;
			}

			@Override
			public V setValue(V value) {
				return null;
			}
		};
	}

	default Entry<K, V> createEntry(K key) {
		return new DefaultEntry<>(key, getDefaultValue());
	}

	default N getNode(Object key) {
		N root = getRoot();
		if(root == null)
			return null;
		return (N) root.findValue(keyEntry((K) key));
	}

	default N getEndNode(boolean first) {
		N ret = getRoot();
		if(ret == null)
			return null;
		while(ret.getChild(first) != null)
			ret = (N) ret.getChild(first);
		return ret;
	}

	default void removeNode(N node) {
		setRoot((N) node.delete());
	}

	default Iterator<N> nodeIterator() {
		return nodeIterator(true, null, true, null, true);
	}

	default Iterator<N> nodeIterator(boolean forward, K start, boolean includeStart, K end, boolean includeEnd) {
		N startNode;
		N root = getRoot();
		if(root == null)
			startNode = null;
		else if(start == null)
			startNode = getEndNode(forward);
		else
			startNode = (N) root.findClosestValue(keyEntry(start), true, includeStart);
		Entry<K, V> endEntry = keyEntry(end);

		return new Iterator<N>() {
			private N theLastNode = null;

			private N theNextNode = startNode;

			@Override
			public boolean hasNext() {
				if(theNextNode == null)
					return false;
				if(end == null)
					return true;
				int compare = theNextNode.compare(theNextNode.getValue(), endEntry);
				if(compare > 0)
					return false;
				if(compare == 0 && !includeEnd)
					return false;
				return true;
			}

			@Override
			public N next() {
				N nextNode = theNextNode;
				if(nextNode == null)
					throw new java.util.NoSuchElementException();
				theLastNode = nextNode;
				theNextNode = (N) nextNode.getClosest(forward);
				return theLastNode;
			}

			@Override
			public void remove() {
				if(theLastNode == null)
					throw new IllegalStateException("remove() may only be called after next() and only once after each call to next()");
				setRoot((N) theLastNode.delete());
				theLastNode = null;
			}
		};
	}

	@Override
	default int size() {
		N root = getRoot();
		if(root == null)
			return 0;
		return root.getSize();
	}

	@Override
	default boolean isEmpty() {
		return getRoot() == null;
	}

	@Override
	default boolean containsKey(Object o) {
		return getNode(o) != null;
	}

	@Override
	default boolean containsValue(Object value) {
		N node = getEndNode(true);
		while(node != null) {
			if(Objects.equals(node.getValue().getValue(), value))
				return true;
			node = (N) node.getClosest(false);
		}
		return false;
	}

	@Override
	default K firstKey() {
		N found = getEndNode(true);
		if(found == null)
			throw new java.util.NoSuchElementException();
		return found.getValue().getKey();
	}

	@Override
	default Entry<K, V> firstEntry() {
		N found = getEndNode(true);
		if(found == null)
			throw new java.util.NoSuchElementException();
		return found.getValue();
	}

	@Override
	default K lastKey() {
		N found = getEndNode(false);
		if(found == null)
			throw new java.util.NoSuchElementException();
		return found.getValue().getKey();
	}

	@Override
	default Entry<K, V> lastEntry() {
		N found = getEndNode(false);
		if(found == null)
			throw new java.util.NoSuchElementException();
		return found.getValue();
	}

	default Entry<K, V> getClosestEntry(K key, boolean lesser, boolean withEqual) {
		N root = getRoot();
		if(root == null)
			return null;
		N found = (N) root.findClosestValue(keyEntry(key), lesser, withEqual);
		return found == null ? null : found.getValue();
	}

	@Override
	default Entry<K, V> lowerEntry(K key) {
		return getClosestEntry(key, true, false);
	}

	@Override
	default K lowerKey(K key) {
		Entry<K, V> entry = lowerEntry(key);
		return entry == null ? null : entry.getKey();
	}

	@Override
	default Entry<K, V> floorEntry(K key) {
		return getClosestEntry(key, true, true);
	}

	@Override
	default K floorKey(K key) {
		Entry<K, V> entry = floorEntry(key);
		return entry == null ? null : entry.getKey();
	}

	@Override
	default Entry<K, V> ceilingEntry(K key) {
		return getClosestEntry(key, false, true);
	}

	@Override
	default K ceilingKey(K key) {
		Entry<K, V> entry = ceilingEntry(key);
		return entry == null ? null : entry.getKey();
	}

	@Override
	default Entry<K, V> higherEntry(K key) {
		return getClosestEntry(key, false, false);
	}

	@Override
	default K higherKey(K key) {
		Entry<K, V> entry = higherEntry(key);
		return entry == null ? null : entry.getKey();
	}

	@Override
	default Entry<K, V> pollFirstEntry() {
		N found = getEndNode(true);
		if(found == null)
			return null;
		Entry<K, V> ret = found.getValue();
		setRoot((N) found.delete());
		return ret;
	}

	@Override
	default Entry<K, V> pollLastEntry() {
		N found = getEndNode(false);
		if(found == null)
			return null;
		Entry<K, V> ret = found.getValue();
		setRoot((N) found.delete());
		return ret;
	}

	@Override
	default V get(Object key) {
		N node = getNode(key);
		if(node == null)
			return null;
		return node.getValue().getValue();
	}

	@Override
	default V put(K key, V value) {
		N root = getRoot();
		if(root == null) {
			setRoot(createRoot(key, value));
			return null;
		}
		TreeOpResult result = root.add(new RedBlackTreeMap.DefaultEntry<>(key, value), false);
		if(result.getFoundNode() != null)
			return ((N) result.getFoundNode()).getValue().setValue(value);
		else
			return null;
	}

	@Override
	default void putAll(Map<? extends K, ? extends V> m) {
		for(Entry<? extends K, ? extends V> e : m.entrySet())
			put(e.getKey(), e.getValue());
	}

	@Override
	default V remove(Object key) {
		N found = getNode(key);
		if(found != null) {
			V ret = found.getValue().getValue();
			setRoot((N) found.delete());
			return ret;
		}
		return null;
	}

	@Override
	default void clear() {
		setRoot(null);
	}

	@Override
	default NavigableMap<K, V> descendingMap() {
		return getSubMap(true, null, true, null, true);
	}

	@Override
	default NavigableSet<K> navigableKeySet() {
		return new KeySet<>(this, false, null, true, null, true);
	}

	@Override
	default NavigableSet<K> keySet() {
		return navigableKeySet();
	}

	@Override
	default NavigableSet<K> descendingKeySet() {
		return new KeySet<>(this, true, null, true, null, true);
	}

	@Override
	default Collection<V> values() {
		return new ValueCollection<>(entrySet());
	}

	@Override
	default RedBlackTreeSet<Entry<K, V>, N> entrySet() {
		return new EntrySet<>(this);
	}

	default NavigableMap<K, V> getSubMap(boolean reverse, K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		return new SubMap<>(this, reverse, fromKey, fromInclusive, toKey, toInclusive);
	}

	@Override
	default NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		return getSubMap(false, fromKey, fromInclusive, toKey, toInclusive);
	}

	@Override
	default NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
		return getSubMap(false, null, true, toKey, inclusive);
	}

	@Override
	default NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
		return getSubMap(false, fromKey, inclusive, null, true);
	}

	@Override
	default NavigableMap<K, V> subMap(K fromKey, K toKey) {
		return getSubMap(false, fromKey, true, toKey, false);
	}

	@Override
	default NavigableMap<K, V> headMap(K toKey) {
		return getSubMap(false, null, true, toKey, false);
	}

	@Override
	default NavigableMap<K, V> tailMap(K fromKey) {
		return getSubMap(false, fromKey, true, null, true);
	}

	static class EntrySet<K, V, N extends ValuedRedBlackNode<Entry<K, V>>> implements RedBlackTreeSet<Entry<K, V>, N> {
		private final RedBlackTreeMap<K, V, N> theMap;

		public EntrySet(RedBlackTreeMap<K, V, N> map) {
			theMap = map;
		}

		@Override
		public Comparator<? super java.util.Map.Entry<K, V>> comparator() {
			return (e1, e2) -> theMap.comparator().compare(e1.getKey(), e2.getKey());
		}

		@Override
		public N getRoot() {
			return theMap.getRoot();
		}

		@Override
		public N createRoot(java.util.Map.Entry<K, V> value) {
			return theMap.createRoot(value.getKey(), value.getValue());
		}

		@Override
		public void setRoot(N root) {
			theMap.setRoot(root);
		}
	}

	static class KeySet<K, V> extends RedBlackTreeSet.MappedSubSet<Entry<K, V>, K> {
		public KeySet(RedBlackTreeMap<K, V, ?> map, boolean reverse, K minKey, boolean includeMin, K maxKey, boolean includeMax) {
			super(map.entrySet(), Entry::getKey, map::createEntry, reverse, minKey, includeMin, maxKey, includeMax);
		}
	}

	static class ValueCollection<K, V> implements Collection<V> {
		private final Collection<? extends Entry<K, V>> theEntries;

		public ValueCollection(Collection<? extends Entry<K, V>> entries) {
			theEntries = entries;
		}

		@Override
		public int size() {
			return theEntries.size();
		}

		@Override
		public boolean isEmpty() {
			return theEntries.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			for(Entry<K, V> entry : theEntries)
				if(Objects.equals(entry.getValue(), o))
					return true;
			return false;
		}

		@Override
		public Iterator<V> iterator() {
			return new Iterator<V>() {
				private final Iterator<? extends Entry<K, V>> backing = theEntries.iterator();

				@Override
				public boolean hasNext() {
					return backing.hasNext();
				}

				@Override
				public V next() {
					return backing.next().getValue();
				}

				@Override
				public void remove() {
					backing.remove();
				}
			};
		}

		@Override
		public Object [] toArray() {
			Object [] ret = theEntries.toArray();
			for(int i = 0; i < ret.length; i++)
				ret[i] = ((Entry<K, V>) ret[i]).getValue();
			return ret;
		}

		@Override
		public <T> T [] toArray(T [] a) {
			int size = size();
			if(a.length < size)
				a = (T []) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
			int i = 0;
			for(V value : this)
				a[i++] = (T) value;
			return a;
		}

		@Override
		public boolean add(V e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean remove(Object o) {
			Iterator<V> iter = iterator();
			while(iter.hasNext()) {
				if(Objects.equals(iter.next(), o)) {
					iter.remove();
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			for(Object o : c)
				if(!contains(o))
					return false;
			return true;
		}

		@Override
		public boolean addAll(Collection<? extends V> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean ret = false;
			for(Object o : c)
				ret |= remove(o);
			return ret;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean ret = false;
			Iterator<V> iter = iterator();
			while(iter.hasNext()) {
				if(!c.contains(iter.next())) {
					ret = true;
					iter.remove();
				}
			}
			return ret;
		}

		@Override
		public void clear() {
			theEntries.clear();
		}
	}

	static class SubMap<K, V> implements NavigableMap<K, V> {
		private final RedBlackTreeMap<K, V, ?> theMap;

		private final boolean isReversed;

		private final K theMinKey;

		private final boolean isMinInclusive;

		private final K theMaxKey;

		private final boolean isMaxInclusive;

		public SubMap(RedBlackTreeMap<K, V, ?> map, boolean reversed, K minKey, boolean minInclusive, K maxKey, boolean maxInclusive) {
			super();
			theMap = map;
			isReversed = reversed;
			theMinKey = minKey;
			isMinInclusive = minInclusive;
			theMaxKey = maxKey;
			isMaxInclusive = maxInclusive;
		}

		public RedBlackTreeMap<K, V, ?> getTreeMap() {
			return theMap;
		}

		public boolean isReversed() {
			return isReversed;
		}

		public K getMinKey() {
			return theMinKey;
		}

		public boolean isMinInclusive() {
			return isMinInclusive;
		}

		public K getMaxKey() {
			return theMaxKey;
		}

		public boolean isMaxInclusive() {
			return isMaxInclusive;
		}

		@Override
		public Comparator<? super K> comparator() {
			Comparator<? super K> compare = theMap.comparator();
			if(isReversed())
				compare = compare.reversed();
			return compare;
		}

		public ValuedRedBlackNode<Entry<K, V>> getEndNode(boolean first) {
			ValuedRedBlackNode<Entry<K, V>> ret = theMap.getRoot();
			if(ret == null)
				return null;

			if(isReversed)
				first = !first;
			K min = first ? theMinKey : theMaxKey;
			boolean include = first ? isMinInclusive : isMaxInclusive;
			if(min == null) {
				while(ret.getChild(first) != null)
					ret = (ValuedRedBlackNode<Entry<K, V>>) ret.getChild(first);
			} else {
				ret = ret.findClosestValue(theMap.keyEntry(min), first, include);
			}
			return ret;
		}

		@Override
		public K firstKey() {
			ValuedRedBlackNode<Entry<K, V>> node = getEndNode(true);
			if(node == null)
				throw new java.util.NoSuchElementException();
			return node.getValue().getKey();
		}

		@Override
		public K lastKey() {
			ValuedRedBlackNode<Entry<K, V>> node = getEndNode(false);
			if(node == null)
				throw new java.util.NoSuchElementException();
			return node.getValue().getKey();
		}

		@Override
		public Entry<K, V> firstEntry() {
			ValuedRedBlackNode<Entry<K, V>> node = getEndNode(true);
			if(node == null)
				throw new java.util.NoSuchElementException();
			return node.getValue();
		}

		@Override
		public Entry<K, V> lastEntry() {
			ValuedRedBlackNode<Entry<K, V>> node = getEndNode(false);
			if(node == null)
				throw new java.util.NoSuchElementException();
			return node.getValue();
		}

		@Override
		public Entry<K, V> pollFirstEntry() {
			ValuedRedBlackNode<Entry<K, V>> node = getEndNode(true);
			if(node == null)
				return null;
			Entry<K, V> ret = node.getValue();
			((RedBlackTreeMap<K, V, ValuedRedBlackNode<Entry<K, V>>>) theMap).setRoot((ValuedRedBlackNode<Entry<K, V>>) node.delete());
			return ret;
		}

		@Override
		public Entry<K, V> pollLastEntry() {
			ValuedRedBlackNode<Entry<K, V>> node = getEndNode(false);
			if(node == null)
				return null;
			Entry<K, V> ret = node.getValue();
			((RedBlackTreeMap<K, V, ValuedRedBlackNode<Entry<K, V>>>) theMap).setRoot((ValuedRedBlackNode<Entry<K, V>>) node.delete());
			return ret;
		}

		@Override
		public int size() {
			return entrySet().size();
		}

		@Override
		public boolean isEmpty() {
			return size() == 0;
		}

		private boolean checkRange(K key) {
			Comparator<? super K> compare = theMap.comparator();
			if(theMinKey != null && compare.compare(key, theMinKey) < 0)
				return false;
			if(theMaxKey != null && compare.compare(key, theMaxKey) > 0)
				return false;
			return true;
		}

		@Override
		public boolean containsKey(Object key) {
			if(!checkRange((K) key))
				return false;
			return theMap.containsKey(key);
		}

		@Override
		public boolean containsValue(Object value) {
			return values().contains(value);
		}

		@Override
		public V get(Object key) {
			if(!checkRange((K) key))
				return null;
			return theMap.get(key);
		}

		@Override
		public V put(K key, V value) {
			if(!checkRange(key))
				throw new UnsupportedOperationException("Cannot insert an entry into a sub-map that cannot include the key");
			return theMap.put(key, value);
		}

		@Override
		public V remove(Object key) {
			if(!checkRange((K) key))
				return null;
			return theMap.remove(key);
		}

		@Override
		public void putAll(Map<? extends K, ? extends V> m) {
			for(Entry<? extends K, ? extends V> entry : m.entrySet())
				put(entry.getKey(), entry.getValue());
		}

		@Override
		public void clear() {
			entrySet().clear();
		}

		private void checkRange(K key, boolean forLower, boolean includeBound) {
			forLower ^= isReversed;
			Comparator<? super K> comparator = theMap.comparator();
			if(theMinKey != null) {
				int compare = comparator.compare(key, theMinKey);
				if(compare < 0)
					outOfRange(key);
				if(compare == 0) {
					if(!isMinInclusive)
						outOfRange(key);
					if(forLower && !includeBound)
						outOfRange(key);
				}
			}
			if(theMaxKey != null) {
				int compare = comparator.compare(key, theMaxKey);
				if(compare > 0)
					outOfRange(key);
				if(compare == 0) {
					if(!isMaxInclusive)
						outOfRange(key);
					if(!forLower && !includeBound)
						outOfRange(key);
				}
			}
		}

		private void outOfRange(K key) {
			throw new IllegalArgumentException("The key " + key + " is outside the range of this submap");
		}

		@Override
		public Entry<K, V> lowerEntry(K key) {
			checkRange(key, true, false);
			return theMap.lowerEntry(key);
		}

		@Override
		public K lowerKey(K key) {
			checkRange(key, true, false);
			return theMap.lowerKey(key);
		}

		@Override
		public Entry<K, V> floorEntry(K key) {
			checkRange(key, true, false);
			return theMap.floorEntry(key);
		}

		@Override
		public K floorKey(K key) {
			checkRange(key, true, false);
			return theMap.floorKey(key);
		}

		@Override
		public Entry<K, V> ceilingEntry(K key) {
			checkRange(key, true, false);
			return theMap.ceilingEntry(key);
		}

		@Override
		public K ceilingKey(K key) {
			checkRange(key, true, false);
			return theMap.ceilingKey(key);
		}

		@Override
		public Entry<K, V> higherEntry(K key) {
			checkRange(key, true, false);
			return theMap.higherEntry(key);
		}

		@Override
		public K higherKey(K key) {
			checkRange(key, true, false);
			return theMap.higherKey(key);
		}

		@Override
		public NavigableMap<K, V> descendingMap() {
			return new SubMap<>(theMap, !isReversed, theMinKey, isMinInclusive, theMaxKey, isMaxInclusive);
		}

		@Override
		public NavigableSet<K> keySet() {
			return navigableKeySet();
		}

		@Override
		public NavigableSet<K> navigableKeySet() {
			return new KeySet<>(theMap, isReversed, theMinKey, isMinInclusive, theMaxKey, isMaxInclusive);
		}

		@Override
		public NavigableSet<K> descendingKeySet() {
			return new KeySet<>(theMap, !isReversed, theMinKey, isMinInclusive, theMaxKey, isMaxInclusive);
		}

		@Override
		public Collection<V> values() {
			return new ValueCollection<>(entrySet());
		}

		@Override
		public NavigableSet<java.util.Map.Entry<K, V>> entrySet() {
			return theMap.entrySet().getSubSet(isReversed, theMap.keyEntry(theMinKey), isMinInclusive, theMap.keyEntry(theMaxKey),
				isMaxInclusive);
		}

		public NavigableMap<K, V> getSubMap(boolean reverse, K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
			if(isReversed) {
				K tempEl = fromKey;
				fromKey = toKey;
				toKey = tempEl;
				boolean tempB = fromInclusive;
				fromInclusive = toInclusive;
				toInclusive = tempB;
			}

			Comparator<? super K> compare = theMap.comparator();
			if(fromKey != null) {
				if(theMinKey != null && compare.compare(fromKey, theMinKey) < 0)
					throw new IllegalArgumentException("Key " + fromKey + " is outside this sub map's range");
			} else {
				fromKey = theMinKey;
				fromInclusive = isMinInclusive;
			}
			if(toKey != null) {
				if(theMaxKey != null && compare.compare(toKey, theMaxKey) > 0)
					throw new IllegalArgumentException("Key " + toKey + " is outside this sub map's range");
			} else {
				toKey = theMaxKey;
				toInclusive = isMaxInclusive;
			}

			return theMap.getSubMap(reverse ^ isReversed, fromKey, fromInclusive, toKey, toInclusive);
		}

		@Override
		public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
			return getSubMap(false, fromKey, fromInclusive, toKey, toInclusive);
		}

		@Override
		public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
			return getSubMap(false, null, true, toKey, inclusive);
		}

		@Override
		public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
			return getSubMap(false, fromKey, inclusive, null, true);
		}

		@Override
		public SortedMap<K, V> subMap(K fromKey, K toKey) {
			return getSubMap(false, fromKey, true, toKey, false);
		}

		@Override
		public SortedMap<K, V> headMap(K toKey) {
			return getSubMap(false, null, true, toKey, false);
		}

		@Override
		public SortedMap<K, V> tailMap(K fromKey) {
			return getSubMap(false, fromKey, true, null, true);
		}
	}
}
