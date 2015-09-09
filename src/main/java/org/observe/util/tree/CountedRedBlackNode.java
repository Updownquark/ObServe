package org.observe.util.tree;

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.function.Function;

import org.observe.util.tree.RedBlackNode.ValuedRedBlackNode;

/**
 * A red/black node containing a value which also keeps track of the size of the tree structure under each node. This makes insertions and
 * deletions slightly more expensive (still O(log(n))), but makes size() operations O(1) and getIndex() operations O(log(n)) instead of O(n)
 * for both.
 *
 * @param <E> The type of value in this node
 */
public abstract class CountedRedBlackNode<E> extends ValuedRedBlackNode<E> {
	private int theSize;

	private boolean isTransaction;

	private int theTransaction;

	/** @param value The value for the node */
	public CountedRedBlackNode(E value) {
		super(value);
		theSize = 1;
	}

	@Override
	public int getSize() {
		return theSize;
	}

	/** @return The number of nodes stored before this node in the tree */
	public int getIndex() {
		CountedRedBlackNode<E> node = this;
		int ret = size(node.getLeft());
		while(node != null) {
			CountedRedBlackNode<E> parent = node.getParent();
			if(parent != null && parent.getRight() == node) {
				ret += size(parent.getLeft()) + 1;
			}
			node = parent;
		}
		return ret;
	}

	/**
	 * @param finder The finder to find the node to get the index of
	 * @return The number of nodes stored before the found node in the tree, or -1 if the given node is not found
	 */
	public int getIndex(Comparable<? super CountedRedBlackNode<E>> finder) {
		CountedRedBlackNode<E> node = this;
		int compare = finder.compareTo(node);
		int ret = 0;
		while(node != null && compare != 0) {
			if(compare < 0)
				node = node.getLeft();
			else {
				ret += size(node.getLeft()) + 1;
				node = node.getRight();
			}
			if(node != null)
				compare = finder.compareTo(node);
		}

		if(node != null)
			ret += size(node.getLeft());
		else
			return -ret - 1; // There are ret nodes before the insertion point
		return ret;
	}

	@Override
	public CountedRedBlackNode<E> getParent() {
		return (CountedRedBlackNode<E>) super.getParent();
	}

	@Override
	public CountedRedBlackNode<E> getRoot() {
		return (CountedRedBlackNode<E>) super.getRoot();
	}

	@Override
	public CountedRedBlackNode<E> getLeft() {
		return (CountedRedBlackNode<E>) super.getLeft();
	}

	@Override
	public CountedRedBlackNode<E> getRight() {
		return (CountedRedBlackNode<E>) super.getRight();
	}

	@Override
	public CountedRedBlackNode<E> getChild(boolean left) {
		return (CountedRedBlackNode<E>) super.getChild(left);
	}

	@Override
	public CountedRedBlackNode<E> getSibling() {
		return (CountedRedBlackNode<E>) super.getSibling();
	}

	@Override
	public CountedRedBlackNode<E> find(Comparable<RedBlackNode> finder) {
		return (CountedRedBlackNode<E>) super.find(finder);
	}

	@Override
	public CountedRedBlackNode<E> getClosest(boolean left) {
		return (CountedRedBlackNode<E>) super.getClosest(left);
	}

	@Override
	protected void checkValid(Map<String, Object> properties) {
		super.checkValid(properties);
		if(theSize != size(getLeft()) + size(getRight()) + 1)
			throw new IllegalStateException("Size is incorrect: " + this);
	}

	@Override
	protected abstract CountedRedBlackNode<E> createNode(E value);

	/**
	 * This should NEVER be called outside of the {@link CountedRedBlackNode} class, but may be overridden by subclasses if the super is
	 * called.
	 *
	 * @param size The new size
	 */
	protected void setSize(int size) {
		theSize = size;
	}

	private void adjustSize(int diff) {
		if(diff != 0) {
			if(isTransaction) {
				theTransaction += diff;
			} else {
				setSize(theSize + diff);
				CountedRedBlackNode<E> parent = getParent();
				if(parent != null)
					parent.adjustSize(diff);
			}
		}
	}

	private void startCountTransaction() {
		isTransaction = true;
	}

	private void endCountTransaction() {
		isTransaction = false;
		int trans = theTransaction;
		theTransaction = 0;
		adjustSize(trans);
	}

	@Override
	protected RedBlackNode setChild(RedBlackNode child, boolean left) {
		RedBlackNode oldChild = super.setChild(child, left);
		int sizeDiff = size((CountedRedBlackNode<?>) child) - size((CountedRedBlackNode<?>) oldChild);
		adjustSize(sizeDiff);
		return oldChild;
	}

	@Override
	protected void replace(RedBlackNode node) {
		CountedRedBlackNode<E> counted = (CountedRedBlackNode<E>) node;
		CountedRedBlackNode<E> parent = getParent();
		startCountTransaction();
		counted.startCountTransaction();
		if(parent != null)
			parent.startCountTransaction();
		try {
			super.replace(node);
		} finally {
			endCountTransaction();
			counted.endCountTransaction();
			if(parent != null)
				parent.endCountTransaction();
		}
	}

	@Override
	protected void switchWith(RedBlackNode node) {
		CountedRedBlackNode<E> counted = (CountedRedBlackNode<E>) node;
		CountedRedBlackNode<E> parent = getParent();
		startCountTransaction();
		counted.startCountTransaction();
		if(parent != null)
			parent.startCountTransaction();
		try {
			super.switchWith(node);
		} finally {
			endCountTransaction();
			counted.endCountTransaction();
			if(parent != null)
				parent.endCountTransaction();
		}
	}

	@Override
	protected CountedRedBlackNode<E> rotate(boolean left) {
		CountedRedBlackNode<E> countedChild = getChild(!left);
		CountedRedBlackNode<E> parent = getParent();
		startCountTransaction();
		countedChild.startCountTransaction();
		if(parent != null)
			parent.startCountTransaction();
		try {
			return (CountedRedBlackNode<E>) super.rotate(left);
		} finally {
			endCountTransaction();
			countedChild.endCountTransaction();
			if(parent != null)
				parent.endCountTransaction();
		}
	}

	@Override
	public String toString() {
		String ret = super.toString();
		if(getParent() != null && getParent().getParent() == this)
			return ret;
		ret = ret.substring(0, ret.length() - 1);
		ret += ", " + theSize + ") [" + getIndex() + "]";
		return ret;
	}

	/**
	 * @param node The node to get the size of
	 * @return The size of the given node, or 0 if the node is null
	 */
	public static final int size(CountedRedBlackNode<?> node) {
		CountedRedBlackNode<?> counted = node;
		return node == null ? 0 : counted.theSize;
	}

	/**
	 * A simple counted node implementation
	 *
	 * @param <E> The type of value for the node
	 */
	public static class DefaultNode<E> extends CountedRedBlackNode<E> {
		private Comparator<? super E> theCompare;

		/**
		 * @param value The value for the node
		 * @param compare The comparator to compare node values with
		 */
		public DefaultNode(E value, Comparator<? super E> compare) {
			super(value);
			theCompare = compare;
		}

		@Override
		protected CountedRedBlackNode<E> createNode(E value) {
			return new DefaultNode<>(value, theCompare);
		}

		@Override
		protected int compare(E o1, E o2) {
			return theCompare.compare(o1, o2);
		}
	}

	/**
	 * A tree set of counted nodes that improves performance for some operations and adds the ability to get the index of a value
	 *
	 * @param <E> The type of values to store in the set
	 * @param <N> The sub-type of nodes used to store the data
	 */
	public static abstract class CountedRedBlackTreeSet<E, N extends CountedRedBlackNode<E>> implements RedBlackTreeSet<E, N> {
		private N theRoot;

		@Override
		public N getRoot() {
			return theRoot;
		}

		@Override
		public void setRoot(N root) {
			if(root != null)
				root.setRed(false); // Root is black
			theRoot = root;
		}

		/**
		 * @param value The value to get the index for
		 * @return The number of values in this set less than the given value
		 */
		public int indexOf(E value){
			return getRoot().getIndex(node->node.compare(value, node.getValue()));
		}

		@Override
		public <V> NavigableSet<V> getMappedSubSet(Function<? super E, V> outMap, Function<? super V, E> inMap, boolean reverse,
			V fromElement, boolean fromInclusive, V toElement, boolean toInclusive) {
			Function<? super V, E> in = inMap != null ? inMap : v -> (E) v;
			Comparator<? super E> treeComparator = comparator();
			Comparator<V> comparator = (o1, o2) -> treeComparator.compare(in.apply(o1), in.apply(o2));
			if(fromElement != null && toElement != null) {
				int compare = comparator.compare(fromElement, toElement);
				if(compare > 0)
					throw new IllegalArgumentException("Sub Set arguments " + fromElement + " and " + toElement + " are in reverse order");
				if(compare == 0 && (!fromInclusive || !toInclusive))
					return java.util.Collections.emptyNavigableSet();
			}
			return new MappedSubSet<>(this, outMap, inMap, reverse, fromElement, fromInclusive, toElement, toInclusive);
		}

		@Override
		public NodeSet<E, N> nodes(boolean reverse, E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			return new NodeSet<>(this, reverse, fromElement, fromInclusive, toElement, toInclusive);
		}

		@Override
		public String toString() {
			StringBuilder ret = new StringBuilder("{");
			boolean first = true;
			for(Object value : this) {
				if(!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
			ret.append('}');
			return ret.toString();
		}

		/**
		 * The sub set type used by {@link CountedRedBlackNode.CountedRedBlackTreeSet}
		 *
		 * @param <E> The type of elements in the set
		 * @param <V> the type of values in the sub set
		 */
		public static class MappedSubSet<E, V> extends RedBlackTreeSet.MappedSubSet<E, V> {
			MappedSubSet(CountedRedBlackTreeSet<E, ?> tree, Function<? super E, V> outMap, Function<? super V, E> inMap, boolean reverse,
				V min, boolean includeMin, V max, boolean includeMax) {
				super(tree, outMap, inMap, reverse, min, includeMin, max, includeMax);
			}

			@Override
			public CountedRedBlackNode<E> getEndNode(boolean first) {
				return (CountedRedBlackNode<E>) super.getEndNode(first);
			}

			@Override
			public int size() {
				CountedRedBlackNode<E> first = getEndNode(true);
				CountedRedBlackNode<E> last = getEndNode(false);
				int idxDiff = last.getIndex() - first.getIndex() + 1;
				if(isReversed())
					idxDiff = -idxDiff;
				return idxDiff;
			}
		}

		/**
		 * @param <E> The type of value in the set
		 * @param <N> The sub-type of node in the set
		 */
		public static class NodeSet<E, N extends CountedRedBlackNode<E>> extends RedBlackTreeSet.NodeSet<E, N> {
			/**
			 * @param set The tree set to wrap
			 * @param reversed Whether to return nodes in reverse of the ordering of the set's values
			 * @param min The minimum value for the node set
			 * @param minInclusive Whether the node for the minimum value is included in the node set
			 * @param max The maximum value for the node set
			 * @param maxInclusive Whether the node for the maximum value is included in the node set
			 */
			public NodeSet(CountedRedBlackTreeSet<E, N> set, boolean reversed, E min, boolean minInclusive, E max, boolean maxInclusive) {
				super(set, reversed, min, minInclusive, max, maxInclusive);
			}

			@Override
			public int size() {
				CountedRedBlackNode<E> first = getEndNode(true);
				CountedRedBlackNode<E> last = getEndNode(false);
				int idxDiff = last.getIndex() - first.getIndex();
				if(isReversed())
					idxDiff = -idxDiff;
				return idxDiff;
			}
		}
	}

	/**
	 * A tree map of counted nodes that improves performance for some operations and adds the ability to get the index of a key
	 *
	 * @param <K> The key type of the map
	 * @param <V> The value type of the map
	 * @param <N> The type of nodes used to store the data
	 */
	public static abstract class CountedRedBlackTreeMap<K, V, N extends CountedRedBlackNode<Map.Entry<K, V>>> implements
	RedBlackTreeMap<K, V, N>, Cloneable {
		private N theRoot;

		@Override
		public N getRoot() {
			return theRoot;
		}

		@Override
		public void setRoot(N root) {
			if(root != null)
				root.setRed(false);
			theRoot = root;
		}

		/**
		 * @param index The index of the entry to get
		 * @return The entry at the given index
		 */
		public Map.Entry<K, V> get(int index) {
			return getNode(index).getValue();
		}

		/**
		 * @param index The index of the node to get
		 * @return The node at the given index
		 */
		public N getNode(int index) {
			N node = theRoot;
			if(node == null || index < 0)
				throw new IndexOutOfBoundsException(index + " of " + size());
			int past = 0;
			while(true) {
				N left = (N) node.getLeft();
				int leftSize = CountedRedBlackNode.size(left);
				if(leftSize > (index - past))
					node = left;
				else if(leftSize == (index - past))
					return node;
				else {
					N right = (N) node.getRight();
					if(right == null) {
						throw new IndexOutOfBoundsException(index + " of " + past);
					} else {
						past += leftSize + 1;
						node = right;
					}
				}
			}
		}

		/**
		 * @param key The key to get the index of
		 * @return The number of keys in this map less than the given key
		 */
		public int indexOfKey(K key) {
			N root = getRoot();
			if(root == null)
				return -1;
			return root.getIndex(node -> node.compare(keyEntry(key), node.getValue()));
		}

		@Override
		public NavigableSet<K> navigableKeySet() {
			return new KeySet<>(this, false, null, true, null, true);
		}

		@Override
		public NavigableSet<K> descendingKeySet() {
			return new KeySet<>(this, true, null, true, null, true);
		}

		@Override
		public EntrySet<K, V, N> entrySet() {
			return new EntrySet<>(this);
		}

		@Override
		public SubMap<K, V> getSubMap(boolean reverse, K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
			return new SubMap<>(this, reverse, fromKey, fromInclusive, toKey, toInclusive);
		}

		@Override
		public CountedRedBlackTreeMap<K, V, N> clone() {
			CountedRedBlackTreeMap<K, V, N> ret;
			try {
				ret = (CountedRedBlackTreeMap<K, V, N>) super.clone();
			} catch(CloneNotSupportedException e) {
				throw new IllegalStateException("Not cloneable");
			}
			ret.theRoot = (N) theRoot.clone();
			return ret;
		}

		@Override
		public String toString() {
			return entrySet().toString();
		}

		/**
		 * Implements {@link CountedRedBlackNode.CountedRedBlackTreeMap#entrySet()}
		 *
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 * @param <N> The sub-type of node storing the values
		 */
		public static class EntrySet<K, V, N extends CountedRedBlackNode<Entry<K, V>>> extends CountedRedBlackTreeSet<Entry<K, V>, N> {
			private final CountedRedBlackTreeMap<K, V, N> theMap;

			/** @param map The map to represent the entries of */
			public EntrySet(CountedRedBlackTreeMap<K, V, N> map) {
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
			public N createNode(java.util.Map.Entry<K, V> value) {
				return theMap.createNode(value.getKey(), value.getValue());
			}

			@Override
			public void setRoot(N root) {
				theMap.setRoot(root);
			}
		}

		/**
		 * Implements {@link CountedRedBlackNode.CountedRedBlackTreeMap#keySet()}
		 *
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 */
		public static class KeySet<K, V> extends CountedRedBlackTreeSet.MappedSubSet<Entry<K, V>, K> {
			/**
			 * @param map The map to create the key set for
			 * @param reverse Whether to reverse the map's keys
			 * @param minKey The key to bound the map on the low side
			 * @param includeMin Whether to include the fromKey in the key set
			 * @param maxKey The key bound the map on the high side
			 * @param includeMax Whether to include the endKey in the key set
			 */
			public KeySet(CountedRedBlackTreeMap<K, V, ?> map, boolean reverse, K minKey, boolean includeMin, K maxKey, boolean includeMax) {
				super(map.entrySet(), Entry::getKey, map::createEntry, reverse, minKey, includeMin, maxKey, includeMax);
			}
		}

		/**
		 * Implements {@link CountedRedBlackNode.CountedRedBlackTreeMap#getSubMap(boolean, Object, boolean, Object, boolean)}
		 *
		 * @param <K> The key type of the map
		 * @param <V> The value type of the map
		 */
		public static class SubMap<K, V> extends RedBlackTreeMap.SubMap<K, V> {
			/**
			 * @param map The map to sub-map
			 * @param reversed Whether to reverse the map's keys
			 * @param minKey The key to bound the map on the low side
			 * @param minInclusive Whether to include the fromKey in the sub-map
			 * @param maxKey The key bound the map on the high side
			 * @param maxInclusive Whether to include the endKey in the sub-map
			 */
			public SubMap(CountedRedBlackTreeMap<K, V, ?> map, boolean reversed, K minKey, boolean minInclusive, K maxKey,
				boolean maxInclusive) {
				super(map, reversed, minKey, minInclusive, maxKey, maxInclusive);
			}

			@Override
			public CountedRedBlackNode<Entry<K, V>> getEndNode(boolean first) {
				return (CountedRedBlackNode<Entry<K, V>>) super.getEndNode(first);
			}

			@Override
			public int size() {
				CountedRedBlackNode<Entry<K, V>> first = getEndNode(true);
				CountedRedBlackNode<Entry<K, V>> last = getEndNode(false);
				int idxDiff = last.getIndex() - first.getIndex();
				if(isReversed())
					idxDiff = -idxDiff;
				return idxDiff;
			}
		}
	}

	/**
	 * A default implementation of the counted tree set
	 *
	 * @param <E> The type of values in the set
	 */
	public static class DefaultTreeSet<E> extends CountedRedBlackTreeSet<E, DefaultNode<E>> {
		private final Comparator<? super E> theCompare;

		/** @param compare The value comparator for the set */
		public DefaultTreeSet(Comparator<? super E> compare) {
			theCompare = compare;
		}

		@Override
		public Comparator<? super E> comparator() {
			return theCompare;
		}

		@Override
		public DefaultNode<E> createNode(E value) {
			return new DefaultNode<>(value, theCompare);
		}
	}

	/**
	 * A default implementation of counted tree map
	 *
	 * @param <K> The key type for the map
	 * @param <V> The value type for the map
	 */
	public static class DefaultTreeMap<K, V> extends CountedRedBlackTreeMap<K, V, DefaultNode<Map.Entry<K, V>>> {
		private final Comparator<? super K> theCompare;

		/** @param compare The key comparator for the map */
		public DefaultTreeMap(Comparator<? super K> compare) {
			theCompare = compare;
		}

		@Override
		public Comparator<? super K> comparator() {
			return theCompare;
		}

		@Override
		public DefaultNode<Entry<K, V>> createNode(K key) {
			return new DefaultNode<>(new RedBlackTreeMap.DefaultEntry<>(key), (o1, o2) -> theCompare.compare(o1.getKey(), o2.getKey()));
		}
	}
}
