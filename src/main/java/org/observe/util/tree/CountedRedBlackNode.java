package org.observe.util.tree;

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.function.Function;

import org.observe.util.tree.RedBlackNode.ValuedRedBlackNode;

public abstract class CountedRedBlackNode<E> extends ValuedRedBlackNode<E> {
	private int theSize;

	private boolean isTransaction;

	private int theTransaction;

	public CountedRedBlackNode(E value) {
		super(value);
		theSize = 1;
	}

	@Override
	public int getSize() {
		return theSize;
	}

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

	public int getIndex(Comparable<RedBlackNode> finder) {
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
			return -ret - 1;
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
		adjustSize(size(child) - size(oldChild));
		return oldChild;
	}

	@Override
	protected void replace(RedBlackNode node) {
		CountedRedBlackNode<E> counted = (CountedRedBlackNode<E>) node;
		CountedRedBlackNode<E> parent = getParent();
		isTransaction = true;
		counted.isTransaction = true;
		if(parent != null)
			parent.startCountTransaction();
		try {
			super.replace(node);
		} finally {
			isTransaction = false;
			counted.isTransaction = false;
			adjustSize(0);
			counted.adjustSize(0);
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
		ret = ret.substring(0, ret.length() - 1);
		ret += ", " + theSize + ") [" + getIndex() + "]";
		return ret;
	}

	private static final int size(RedBlackNode node) {
		CountedRedBlackNode<?> counted = (CountedRedBlackNode<?>) node;
		return node == null ? 0 : counted.theSize;
	}

	public static class DefaultNode<E> extends CountedRedBlackNode<E> {
		private Comparator<? super E> theCompare;

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

	public static abstract class CountedRedBlackTreeSet<E, N extends CountedRedBlackNode<E>> implements RedBlackTreeSet<E, N> {
		private N theRoot;

		@Override
		public N getRoot() {
			return theRoot;
		}

		@Override
		public void setRoot(N root) {
			theRoot = root;
		}

		public int indexOf(E value){
			return getRoot().getIndex(node->((CountedRedBlackNode<E>) node).compare(value, ((CountedRedBlackNode<E>) node).getValue()));
		}

		@Override
		public <V> NavigableSet<V> getMappedSubSet(Function<? super E, V> outMap, Function<? super V, E> inMap, boolean reverse,
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
		public NodeSet<E, N> nodes(boolean reverse, E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
			return new NodeSet<>(this, reverse, fromElement, fromInclusive, toElement, toInclusive);
		}

		/**
		 * The sub set type used by {@link CountedRedBlackNode.CountedRedBlackTreeSet}
		 *
		 * @param <E> The type of elements in the set
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
				int idxDiff = last.getIndex() - first.getIndex();
				if(isReversed())
					idxDiff = -idxDiff;
				return idxDiff;
			}
		}

		public static class NodeSet<E, N extends CountedRedBlackNode<E>> extends RedBlackTreeSet.NodeSet<E, N> {
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

	public static abstract class CountedRedBlackTreeMap<K, V, N extends CountedRedBlackNode<Map.Entry<K, V>>> implements
	RedBlackTreeMap<K, V, N>, Cloneable {
		private N theRoot;

		@Override
		public N getRoot() {
			return theRoot;
		}

		@Override
		public void setRoot(N root) {
			theRoot = root;
		}

		public int indexOfKey(K key) {
			return getRoot().getIndex(
				node -> ((CountedRedBlackNode<Entry<K, V>>) node).compare(keyEntry(key),
					((CountedRedBlackNode<Entry<K, V>>) node).getValue()));
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
		public CountedRedBlackTreeSet<Entry<K, V>, N> entrySet() {
			return new EntrySet<>(this);
		}

		@Override
		public NavigableMap<K, V> getSubMap(boolean reverse, K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
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

		public static class EntrySet<K, V, N extends CountedRedBlackNode<Entry<K, V>>> extends CountedRedBlackTreeSet<Entry<K, V>, N> {
			private final CountedRedBlackTreeMap<K, V, N> theMap;

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
			public N createRoot(java.util.Map.Entry<K, V> value) {
				return theMap.createRoot(value.getKey(), value.getValue());
			}

			@Override
			public void setRoot(N root) {
				theMap.setRoot(root);
			}
		}

		public static class KeySet<K, V> extends CountedRedBlackTreeSet.MappedSubSet<Entry<K, V>, K> {
			public KeySet(CountedRedBlackTreeMap<K, V, ?> map, boolean reverse, K minKey, boolean includeMin, K maxKey, boolean includeMax) {
				super(map.entrySet(), Entry::getKey, map::createEntry, reverse, minKey, includeMin, maxKey, includeMax);
			}
		}

		public static class SubMap<K, V> extends RedBlackTreeMap.SubMap<K, V> {
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

	public static class DefaultTreeSet<E> extends CountedRedBlackTreeSet<E, DefaultNode<E>> {
		private final Comparator<? super E> theCompare;

		public DefaultTreeSet(Comparator<? super E> compare) {
			theCompare = compare;
		}

		@Override
		public Comparator<? super E> comparator() {
			return theCompare;
		}

		@Override
		public DefaultNode<E> createRoot(E value) {
			return new DefaultNode<>(value, theCompare);
		}
	}

	public static class DefaultTreeMap<K, V> extends CountedRedBlackTreeMap<K, V, DefaultNode<Map.Entry<K, V>>> {
		private final Comparator<? super K> theCompare;

		public DefaultTreeMap(Comparator<? super K> compare) {
			theCompare = compare;
		}

		@Override
		public Comparator<? super K> comparator() {
			return theCompare;
		}

		@Override
		public DefaultNode<Entry<K, V>> createRoot(K key, V value) {
			return new DefaultNode<>(new RedBlackTreeMap.DefaultEntry<>(key, value), (o1, o2) -> theCompare.compare(o1.getKey(),
				o2.getKey()));
		}
	}

	public static void main(String [] args) {
		class CountedStringNode extends CountedRedBlackNode<String> {
			public CountedStringNode(String value) {
				super(value);
			}

			@Override
			protected CountedRedBlackNode<String> createNode(String value) {
				return new CountedStringNode(value);
			}

			@Override
			protected int compare(String o1, String o2) {
				return o1.compareTo(o2);
			}
		}
		test(new CountedStringNode("a"), alphaBet('q'));
	}
}
