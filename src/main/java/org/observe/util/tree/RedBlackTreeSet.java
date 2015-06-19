package org.observe.util.tree;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.function.Function;

import org.observe.util.tree.RedBlackNode.ValuedRedBlackNode;

public interface RedBlackTreeSet<E, N extends ValuedRedBlackNode<E>> extends java.util.NavigableSet<E> {
	N getRoot();

	N createRoot(E value);

	void setRoot(N root);

	default N getNode(Object value) {
		N root = getRoot();
		if(root == null)
			return null;
		return (N) root.findValue((E) value);
	}

	default N getEndNode(boolean first) {
		N ret = getRoot();
		if(ret == null)
			return null;
		while(ret.getChild(first) != null)
			ret = (N) ret.getChild(first);
		return ret;
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
	default boolean contains(Object o) {
		return getNode(o) != null;
	}

	@Override
	default Object [] toArray() {
		Object [] ret = new Object[size()];
		int i = 0;
		for(E value : this)
			ret[i++] = value;
		return ret;
	}

	@Override
	default <T> T [] toArray(T [] a) {
		int size = size();
		if(a.length < size)
			a = (T []) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
		int i = 0;
		for(E value : this)
			a[i++] = (T) value;
		return a;
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		for(Object o : c)
			if(!contains(o))
				return false;
		return true;
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		Iterator<E> iter = iterator();
		boolean changed = false;
		while(iter.hasNext()) {
			if(!c.contains(iter.next())) {
				changed = true;
				iter.remove();
			}
		}
		return changed;
	}

	@Override
	default Iterator<E> iterator() {
		return iterator(true, null, true, null, true);
	}

	default Iterator<E> iterator(boolean forward, E start, boolean includeStart, E end, boolean includeEnd) {
		return new Iterator<E>() {
			private final Iterator<N> backing = nodeIterator(forward, start, includeStart, end, includeEnd);

			@Override
			public boolean hasNext() {
				return backing.hasNext();
			}

			@Override
			public E next() {
				return backing.next().getValue();
			}

			@Override
			public void remove() {
				backing.remove();
			}
		};
	}

	default Iterator<N> nodeIterator(boolean forward, E start, boolean includeStart, E end, boolean includeEnd) {
		N startNode;
		N root = getRoot();
		if(root == null)
			startNode = null;
		else if(start == null)
			startNode = getEndNode(forward);
		else
			startNode = (N) root.findClosestValue(start, true, includeStart);

		return new Iterator<N>() {
			private N theLastNode = null;

			private N theNextNode = startNode;

			@Override
			public boolean hasNext() {
				if(theNextNode == null)
					return false;
				if(end == null)
					return true;
				int compare = theNextNode.compare(theNextNode.getValue(), end);
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
	default boolean add(E value) {
		int preSize = size();
		simpleAdd(value);
		return preSize != size();
	}

	/**
	 * Like {@link #add(Object)}, but doesn't incur the O(n) cost of checking size before and after addition to see whether the addition
	 * succeeded
	 *
	 * @param value The value to add
	 */
	default void simpleAdd(E value) {
		N root = getRoot();
		if(root == null)
			setRoot(createRoot(value));
		else
			setRoot((N) root.add(value, true).getNewRoot());
	}

	@Override
	default boolean remove(Object value) {
		N found = getNode(value);
		if(found != null) {
			setRoot((N) found.delete());
			return true;
		}
		return false;
	}

	@Override
	default boolean addAll(Collection<? extends E> c) {
		boolean ret = false;
		for(E value : c)
			ret |= add(value);
		return ret;
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		boolean ret = false;
		for(Object o : c)
			ret |= remove(o);
		return ret;
	}

	@Override
	default void clear() {
		setRoot(null);
	}

	@Override
	default E first() {
		N found = getEndNode(true);
		if(found == null)
			throw new java.util.NoSuchElementException();
		return found.getValue();
	}

	@Override
	default E last() {
		N found = getEndNode(false);
		if(found == null)
			throw new java.util.NoSuchElementException();
		return found.getValue();
	}

	default N getClosestNode(E value, boolean lesser, boolean withEqual) {
		N root = getRoot();
		if(root == null)
			return null;
		return (N) root.findClosestValue(value, lesser, withEqual);
	}

	default E getClosest(E value, boolean lesser, boolean withEqual) {
		N root = getRoot();
		if(root == null)
			return null;
		N found = (N) root.findClosestValue(value, lesser, withEqual);
		return found == null ? null : found.getValue();
	}

	@Override
	default E lower(E e) {
		return getClosest(e, true, false);
	}

	@Override
	default E floor(E e) {
		return getClosest(e, true, true);
	}

	@Override
	default E ceiling(E e) {
		return getClosest(e, false, true);
	}

	@Override
	default E higher(E e) {
		return getClosest(e, false, false);
	}

	@Override
	default E pollFirst() {
		N found = getEndNode(true);
		if(found == null)
			return null;
		E ret = found.getValue();
		setRoot((N) found.delete());
		return ret;
	}

	@Override
	default E pollLast() {
		N found = getEndNode(false);
		if(found == null)
			return null;
		E ret = found.getValue();
		setRoot((N) found.delete());
		return ret;
	}

	@Override
	default NavigableSet<E> descendingSet() {
		return getSubSet(true, null, true, null, true);
	}

	@Override
	default Iterator<E> descendingIterator() {
		return iterator(false, null, true, null, true);
	}

	default NavigableSet<E> getSubSet(boolean reverse, E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return getMappedSubSet(null, null, reverse, fromElement, fromInclusive, toElement, toInclusive);
	}

	default <V> NavigableSet<V> getMappedSubSet(Function<? super E, V> outMap, Function<? super V, E> inMap, boolean reverse,
		V fromElement, boolean fromInclusive, V toElement, boolean toInclusive) {
		Comparator<? super E> treeComparator = comparator();
		Comparator<V> comparator = (o1, o2) -> treeComparator.compare(inMap.apply(o1), inMap.apply(o2));
		if(fromElement != null && toElement != null) {
			int compare = comparator.compare(fromElement, toElement);
			if(compare < 0)
				throw new IllegalArgumentException("Sub Set arguments " + fromElement + " and " + toElement + " are in reverse order");
			if(compare == 0 && (!fromInclusive || !toInclusive))
				return java.util.Collections.emptyNavigableSet();
		}
		return new MappedSubSet<>(this, outMap, inMap, reverse, fromElement, fromInclusive, toElement, toInclusive);
	}

	@Override
	default NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return getSubSet(false, fromElement, fromInclusive, toElement, toInclusive);
	}

	@Override
	default NavigableSet<E> headSet(E toElement, boolean inclusive) {
		return getSubSet(false, null, true, toElement, inclusive);
	}

	@Override
	default NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
		return getSubSet(false, fromElement, inclusive, null, true);
	}

	@Override
	default SortedSet<E> subSet(E fromElement, E toElement) {
		return getSubSet(false, fromElement, true, toElement, false);
	}

	@Override
	default SortedSet<E> headSet(E toElement) {
		return getSubSet(false, null, true, toElement, false);
	}

	@Override
	default SortedSet<E> tailSet(E fromElement) {
		return getSubSet(false, fromElement, true, null, true);
	}

	default NodeSet<E, N> nodes() {
		return nodes(false, null, true, null, true);
	}

	default NodeSet<E, N> nodes(boolean reverse, E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return new NodeSet<>(this, reverse, fromElement, fromInclusive, toElement, toInclusive);
	}

	static class MappedSubSet<E, V> implements java.util.NavigableSet<V> {
		private final RedBlackTreeSet<E, ?> theTreeSet;
		private final Function<? super E, V> theOutMap;
		private final Function<? super V, E> theInMap;

		private final boolean isReversed;

		private final V theMin;
		private final boolean isMinInclusive;

		private final V theMax;
		private final boolean isMaxInclusive;

		MappedSubSet(RedBlackTreeSet<E, ?> tree, Function<? super E, V> outMap, Function<? super V, E> inMap, boolean reverse, V min,
			boolean includeMin, V max, boolean includeMax) {
			theTreeSet = tree;
			theOutMap = outMap == null ? value -> (V) value : outMap;
			theInMap = inMap == null ? value -> (E) value : inMap;
			isReversed = reverse;
			theMin = min;
			isMinInclusive = includeMin;
			theMax = max;
			isMaxInclusive = includeMax;
		}

		public RedBlackTreeSet<E, ?> getTreeSet() {
			return theTreeSet;
		}

		public boolean isReversed() {
			return isReversed;
		}

		public V getMin() {
			return theMin;
		}

		public boolean isMinInclusive() {
			return isMinInclusive;
		}

		public V getMax() {
			return theMax;
		}

		public boolean isMaxInclusive() {
			return isMaxInclusive;
		}

		public Comparator<? super V> forwardComparator() {
			Comparator<? super E> compare = theTreeSet.comparator();
			return (o1, o2) -> compare.compare(theInMap.apply(o1), theInMap.apply(o2));
		}

		@Override
		public Comparator<? super V> comparator() {
			Comparator<? super V> compare = forwardComparator();
			if(isReversed())
				compare = compare.reversed();
			return compare;
		}

		public ValuedRedBlackNode<E> getEndNode(boolean first) {
			ValuedRedBlackNode<E> ret = theTreeSet.getRoot();
			if(ret == null)
				return null;

			if(isReversed)
				first = !first;
			V min = first ? theMin : theMax;
			boolean include = first ? isMinInclusive : isMaxInclusive;
			if(min == null) {
				while(ret.getChild(first) != null)
					ret = (ValuedRedBlackNode<E>) ret.getChild(first);
			} else {
				ret = ret.findClosestValue(theInMap.apply(min), first, include);
			}
			return ret;
		}

		@Override
		public V first() {
			ValuedRedBlackNode<E> node = getEndNode(true);
			if(node == null)
				throw new java.util.NoSuchElementException();
			return theOutMap.apply(node.getValue());
		}

		@Override
		public V last() {
			ValuedRedBlackNode<E> node = getEndNode(false);
			if(node == null)
				throw new java.util.NoSuchElementException();
			return theOutMap.apply(node.getValue());
		}

		private void checkRange(V e, boolean forLower, boolean includeBound) {
			forLower ^= isReversed;
			Comparator<? super V> comparator = forwardComparator();
			if(theMin!=null){
				int compare = comparator.compare(e, theMin);
				if(compare < 0)
					outOfRange(e);
				if(compare == 0) {
					if(!isMinInclusive)
						outOfRange(e);
					if(forLower && !includeBound)
						outOfRange(e);
				}
			}
			if(theMax != null) {
				int compare = comparator.compare(e, theMax);
				if(compare > 0)
					outOfRange(e);
				if(compare == 0) {
					if(!isMaxInclusive)
						outOfRange(e);
					if(!forLower && !includeBound)
						outOfRange(e);
				}
			}
		}

		private void outOfRange(V e) {
			throw new IllegalArgumentException("The value " + e + " is outside the range of this subset");
		}

		@Override
		public V lower(V e) {
			checkRange(e, true, false);
			E mapped = theInMap.apply(e);
			E found = theTreeSet.lower(mapped);
			if(found == null)
				return null;
			return theOutMap.apply(found);
		}

		@Override
		public V floor(V e) {
			checkRange(e, true, true);
			E mapped = theInMap.apply(e);
			E found = theTreeSet.floor(mapped);
			if(found == null)
				return null;
			return theOutMap.apply(found);
		}

		@Override
		public V ceiling(V e) {
			checkRange(e, false, true);
			E mapped = theInMap.apply(e);
			E found = theTreeSet.ceiling(mapped);
			if(found == null)
				return null;
			return theOutMap.apply(found);
		}

		@Override
		public V higher(V e) {
			checkRange(e, false, false);
			E mapped = theInMap.apply(e);
			E found = theTreeSet.higher(mapped);
			if(found == null)
				return null;
			return theOutMap.apply(found);
		}

		@Override
		public int size() {
			Iterator<V> iter = iterator();
			int ret = 0;
			while(iter.hasNext()) {
				ret++;
				iter.next();
			}
			return ret;
		}

		@Override
		public boolean isEmpty() {
			return size() == 0;
		}

		private boolean checkRange(V val) {
			Comparator<? super V> compare = forwardComparator();
			if(theMin != null && compare.compare(val, theMin) < 0)
				return false;
			if(theMax != null && compare.compare(val, theMax) > 0)
				return false;
			return true;
		}

		@Override
		public boolean contains(Object o) {
			if(!checkRange((V) o))
				return false;
			return theTreeSet.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			for(Object o : c)
				if(!contains(o))
					return false;
			return true;
		}

		@Override
		public Object [] toArray() {
			Object [] ret = new Object[size()];
			int i = 0;
			for(V value : this)
				ret[i++] = value;
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
		public Iterator<V> iterator() {
			return iterator(true);
		}

		public Iterator<V> iterator(boolean forward) {
			V start, end;
			boolean includeStart, includeEnd;
			if(isReversed) {
				start = theMax;
				end = theMin;
				includeStart = isMaxInclusive;
				includeEnd = isMinInclusive;
			} else {
				start = theMin;
				end = theMax;
				includeStart = isMinInclusive;
				includeEnd = isMaxInclusive;
			}
			Iterator<E> backing = theTreeSet.iterator(forward ^ isReversed, theInMap.apply(start), includeStart, theInMap.apply(end),
				includeEnd);
			return new Iterator<V>() {
				@Override
				public boolean hasNext() {
					return backing.hasNext();
				}

				@Override
				public V next() {
					return theOutMap.apply(backing.next());
				}

				@Override
				public void remove() {
					backing.remove();
				}
			};
		}

		@Override
		public Iterator<V> descendingIterator() {
			return iterator(false);
		}

		@Override
		public V pollFirst() {
			ValuedRedBlackNode<E> node = getEndNode(true);
			if(node == null)
				return null;
			E ret = node.getValue();
			((RedBlackTreeSet<E, ValuedRedBlackNode<E>>) theTreeSet).setRoot((ValuedRedBlackNode<E>) node.delete());
			return theOutMap.apply(ret);
		}

		@Override
		public V pollLast() {
			ValuedRedBlackNode<E> node = getEndNode(false);
			if(node == null)
				return null;
			E ret = node.getValue();
			((RedBlackTreeSet<E, ValuedRedBlackNode<E>>) theTreeSet).setRoot((ValuedRedBlackNode<E>) node.delete());
			return theOutMap.apply(ret);
		}

		@Override
		public boolean add(V e) {
			if(!checkRange(e))
				throw new UnsupportedOperationException("Cannot insert an element into a sub-set that cannot include the element");
			E mapped = theInMap.apply(e);
			return theTreeSet.add(mapped);
		}

		@Override
		public boolean addAll(Collection<? extends V> c) {
			boolean ret = false;
			for(V val : c)
				ret |= add(val);
			return ret;
		}

		@Override
		public boolean remove(Object o) {
			if(!checkRange((V) o))
				return false;
			E mapped = theInMap.apply((V) o);
			return theTreeSet.remove(mapped);
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
			Iterator<V> iter = iterator();
			boolean changed = false;
			while(iter.hasNext()) {
				if(!c.contains(iter.next())) {
					changed = true;
					iter.remove();
				}
			}
			return changed;
		}

		@Override
		public void clear() {
			Iterator<V> iter = iterator();
			while(iter.hasNext()) {
				iter.next();
				iter.remove();
			}
		}

		public NavigableSet<V> getSubSet(boolean reverse, V fromElement, boolean fromInclusive, V toElement, boolean toInclusive) {
			if(isReversed) {
				V tempEl = fromElement;
				fromElement = toElement;
				toElement = tempEl;
				boolean tempB = fromInclusive;
				fromInclusive = toInclusive;
				toInclusive = tempB;
			}

			Comparator<? super V> compare = forwardComparator();
			if(fromElement != null) {
				if(theMin != null && compare.compare(fromElement, theMin) < 0)
					throw new IllegalArgumentException("Element " + fromElement + " is outside this sub set's range");
			} else {
				fromElement = theMin;
				fromInclusive = isMinInclusive;
			}
			if(toElement != null) {
				if(theMax != null && compare.compare(toElement, theMax) > 0)
					throw new IllegalArgumentException("Element " + toElement + " is outside this sub set's range");
			} else {
				toElement = theMax;
				toInclusive = isMaxInclusive;
			}

			return theTreeSet.getMappedSubSet(theOutMap, theInMap, reverse ^ isReversed, fromElement, fromInclusive, toElement, toInclusive);
		}

		@Override
		public NavigableSet<V> descendingSet() {
			return getSubSet(true, null, true, null, true);
		}

		@Override
		public NavigableSet<V> subSet(V fromElement, boolean fromInclusive, V toElement, boolean toInclusive) {
			return getSubSet(false, fromElement, fromInclusive, toElement, toInclusive);
		}

		@Override
		public NavigableSet<V> headSet(V toElement, boolean inclusive) {
			return getSubSet(false, null, true, toElement, inclusive);
		}

		@Override
		public NavigableSet<V> tailSet(V fromElement, boolean inclusive) {
			return getSubSet(false, fromElement, inclusive, null, true);
		}

		@Override
		public SortedSet<V> subSet(V fromElement, V toElement) {
			return getSubSet(false, fromElement, true, toElement, false);
		}

		@Override
		public SortedSet<V> headSet(V toElement) {
			return getSubSet(false, null, true, toElement, false);
		}

		@Override
		public SortedSet<V> tailSet(V fromElement) {
			return getSubSet(false, fromElement, true, null, true);
		}
	}

	static class NodeSet<E, N extends ValuedRedBlackNode<E>> implements NavigableSet<N> {
		private final RedBlackTreeSet<E, N> theSet;

		private final boolean isReversed;

		private final E theMin;
		private final boolean isMinInclusive;

		private final E theMax;
		private final boolean isMaxInclusive;

		public NodeSet(RedBlackTreeSet<E, N> set, boolean reversed, E min, boolean minInclusive, E max, boolean maxInclusive) {
			super();
			theSet = set;
			isReversed = reversed;
			theMin = min;
			isMinInclusive = minInclusive;
			theMax = max;
			isMaxInclusive = maxInclusive;
		}

		public RedBlackTreeSet<E, ?> getTreeSet() {
			return theSet;
		}

		public boolean isReversed() {
			return isReversed;
		}

		public E getMin() {
			return theMin;
		}

		public boolean isMinInclusive() {
			return isMinInclusive;
		}

		public E getMax() {
			return theMax;
		}

		public boolean isMaxInclusive() {
			return isMaxInclusive;
		}

		@Override
		public Comparator<? super N> comparator() {
			return (node1, node2) -> theSet.comparator().compare(node1.getValue(), node2.getValue());
		}

		public N getEndNode(boolean first) {
			N ret = theSet.getRoot();
			if(ret == null)
				return null;

			if(isReversed)
				first = !first;
			E min = first ? theMin : theMax;
			boolean include = first ? isMinInclusive : isMaxInclusive;
			if(min == null) {
				while(ret.getChild(first) != null)
					ret = (N) ret.getChild(first);
			} else {
				ret = (N) ret.findClosestValue(min, first, include);
			}
			return ret;
		}

		@Override
		public N first() {
			N node = getEndNode(true);
			if(node == null)
				throw new java.util.NoSuchElementException();
			return node;
		}

		@Override
		public N last() {
			N node = getEndNode(false);
			if(node == null)
				throw new java.util.NoSuchElementException();
			return node;
		}

		public void checkRange(E e, boolean forLower, boolean includeBound) {
			forLower ^= isReversed;
			Comparator<? super E> comparator = theSet.comparator();
			if(theMin != null) {
				int compare = comparator.compare(e, theMin);
				if(compare < 0)
					outOfRange(e);
				if(compare == 0) {
					if(!isMinInclusive)
						outOfRange(e);
					if(forLower && !includeBound)
						outOfRange(e);
				}
			}
			if(theMax != null) {
				int compare = comparator.compare(e, theMax);
				if(compare > 0)
					outOfRange(e);
				if(compare == 0) {
					if(!isMaxInclusive)
						outOfRange(e);
					if(!forLower && !includeBound)
						outOfRange(e);
				}
			}
		}

		private void outOfRange(E e) {
			throw new IllegalArgumentException("The value " + e + " is outside the range of this subset");
		}

		@Override
		public N lower(N e) {
			return lower(e.getValue());
		}

		public N lower(E value) {
			checkRange(value, true, false);
			return theSet.getClosestNode(value, true, false);
		}

		@Override
		public N floor(N e) {
			return floor(e.getValue());
		}

		public N floor(E value) {
			checkRange(value, true, true);
			return theSet.getClosestNode(value, true, true);
		}

		@Override
		public N ceiling(N e) {
			return ceiling(e.getValue());
		}

		public N ceiling(E value) {
			checkRange(value, false, true);
			return theSet.getClosestNode(value, false, true);
		}

		@Override
		public N higher(N e) {
			return higher(e.getValue());
		}

		public N higher(E value) {
			checkRange(value, false, false);
			return theSet.getClosestNode(value, false, false);
		}

		@Override
		public int size() {
			Iterator<N> iter = iterator();
			int ret = 0;
			while(iter.hasNext()) {
				ret++;
				iter.next();
			}
			return ret;
		}

		@Override
		public boolean isEmpty() {
			return size() == 0;
		}

		public boolean checkRange(E val) {
			Comparator<? super E> compare = theSet.comparator();
			if(theMin != null && compare.compare(val, theMin) < 0)
				return false;
			if(theMax != null && compare.compare(val, theMax) > 0)
				return false;
			return true;
		}

		@Override
		public boolean contains(Object o) {
			if(o instanceof ValuedRedBlackNode)
				o = ((ValuedRedBlackNode<?>) o).getValue();
			if(!checkRange((E) o))
				return false;
			return theSet.contains(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			for(Object o : c)
				if(!contains(o))
					return false;
			return true;
		}

		@Override
		public Object [] toArray() {
			ValuedRedBlackNode<?> [] ret = new ValuedRedBlackNode[size()];
			int i = 0;
			for(N value : this)
				ret[i++] = value;
			return ret;
		}

		@Override
		public <T> T [] toArray(T [] a) {
			int size = size();
			if(a.length < size)
				a = (T []) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
			int i = 0;
			for(N value : this)
				a[i++] = (T) value;
			return a;
		}

		@Override
		public Iterator<N> iterator() {
			return iterator(true);
		}

		public Iterator<N> iterator(boolean forward) {
			E start, end;
			boolean includeStart, includeEnd;
			if(isReversed) {
				start = theMax;
				end = theMin;
				includeStart = isMaxInclusive;
				includeEnd = isMinInclusive;
			} else {
				start = theMin;
				end = theMax;
				includeStart = isMinInclusive;
				includeEnd = isMaxInclusive;
			}
			return theSet.nodeIterator(forward ^ isReversed, start, includeStart, end, includeEnd);
		}

		@Override
		public Iterator<N> descendingIterator() {
			return iterator(false);
		}

		@Override
		public N pollFirst() {
			N node = getEndNode(true);
			if(node == null)
				return null;
			((RedBlackTreeSet<E, ValuedRedBlackNode<E>>) theSet).setRoot((ValuedRedBlackNode<E>) node.delete());
			return node;
		}

		@Override
		public N pollLast() {
			N node = getEndNode(false);
			if(node == null)
				return null;
			((RedBlackTreeSet<E, ValuedRedBlackNode<E>>) theSet).setRoot((ValuedRedBlackNode<E>) node.delete());
			return node;
		}

		@Override
		public boolean add(N e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean addAll(Collection<? extends N> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean remove(Object o) {
			if(o instanceof ValuedRedBlackNode)
				o = ((ValuedRedBlackNode<?>) o).getValue();
			if(!checkRange((E) o))
				return false;
			return theSet.remove(o);
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
			java.util.ArrayList<Object> copy = new java.util.ArrayList<>(c);
			for(int i = 0; i < copy.size(); i++) {
				if(copy.get(i) instanceof ValuedRedBlackNode)
					copy.set(i, ((ValuedRedBlackNode<?>) copy.get(i)).getValue());
			}
			boolean ret = false;
			Iterator<N> iter = iterator();
			while(iter.hasNext()) {
				if(!copy.contains(iter.next().getValue())) {
					iter.remove();
					ret = true;
				}
			}
			return ret;
		}

		@Override
		public void clear() {
			Iterator<N> iter = iterator();
			while(iter.hasNext())
				iter.remove();
		}

		public NodeSet<E, N> getSubSet(boolean reverse, N fromElement, boolean fromInclusive, N toElement, boolean toInclusive) {
			return getSubSet(reverse, fromElement == null ? null : fromElement.getValue(), fromInclusive, toElement == null ? null
				: toElement.getValue(), toInclusive);
		}

		public NodeSet<E, N> getSubSet(boolean reverse, E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			if(isReversed) {
				E tempEl = fromElement;
				fromElement = toElement;
				toElement = tempEl;
				boolean tempB = fromInclusive;
				fromInclusive = toInclusive;
				toInclusive = tempB;
			}

			Comparator<? super E> compare = theSet.comparator();
			if(fromElement != null) {
				if(theMin != null && compare.compare(fromElement, theMin) < 0)
					throw new IllegalArgumentException("Element " + fromElement + " is outside this sub set's range");
			} else {
				fromInclusive = isMinInclusive;
			}
			if(toElement != null) {
				if(theMax != null && compare.compare(toElement, theMax) > 0)
					throw new IllegalArgumentException("Element " + toElement + " is outside this sub set's range");
			} else {
				toInclusive = isMaxInclusive;
			}

			return theSet.nodes(reverse ^ isReversed, fromElement, fromInclusive, toElement, toInclusive);
		}

		@Override
		public NodeSet<E, N> descendingSet() {
			return getSubSet(true, (N) null, true, (N) null, true);
		}

		@Override
		public NodeSet<E, N> subSet(N fromElement, boolean fromInclusive, N toElement, boolean toInclusive) {
			return getSubSet(false, fromElement, fromInclusive, toElement, toInclusive);
		}

		@Override
		public NodeSet<E, N> headSet(N toElement, boolean inclusive) {
			return getSubSet(false, null, true, toElement, inclusive);
		}

		@Override
		public NodeSet<E, N> tailSet(N fromElement, boolean inclusive) {
			return getSubSet(false, fromElement, inclusive, null, true);
		}

		@Override
		public NodeSet<E, N> subSet(N fromElement, N toElement) {
			return getSubSet(false, fromElement, true, toElement, false);
		}

		@Override
		public NodeSet<E, N> headSet(N toElement) {
			return getSubSet(false, null, true, toElement, false);
		}

		@Override
		public NodeSet<E, N> tailSet(N fromElement) {
			return getSubSet(false, fromElement, true, null, true);
		}
	}
}