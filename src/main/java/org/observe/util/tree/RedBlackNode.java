package org.observe.util.tree;

import java.util.Iterator;
import java.util.Map;

public abstract class RedBlackNode implements Comparable<RedBlackNode>, Cloneable {
	public static class TreeOpResult {
		private RedBlackNode theFoundNode;

		private RedBlackNode theNewNode;

		RedBlackNode theNewRoot;

		TreeOpResult(RedBlackNode foundNode, RedBlackNode newNode, RedBlackNode root) {
			theFoundNode = foundNode;
			theNewNode = newNode;
			theNewRoot = root;
		}

		public RedBlackNode getFoundNode() {
			return theFoundNode;
		}

		public RedBlackNode getNewNode() {
			return theNewNode;
		}

		public RedBlackNode getNewRoot() {
			return theNewRoot;
		}
	}

	private boolean isRed;

	private RedBlackNode theParent;

	private RedBlackNode theLeft;

	private RedBlackNode theRight;

	public RedBlackNode() {
		isRed = true;
	}

	public boolean isRed() {
		return isRed;
	}

	public RedBlackNode getParent() {
		return theParent;
	}

	public RedBlackNode getRoot() {
		RedBlackNode ret = this;
		while(ret.getParent() != null)
			ret = ret.getParent();
		return ret;
	}

	public final void checkValid() {
		if(theParent != null)
			throw new IllegalStateException("checkValid() may only be called on the root");
		if(isRed)
			throw new IllegalStateException("The root is red!");

		checkValid(initValidationProperties());
	}

	protected Map<String, Object> initValidationProperties() {
		Map<String, Object> ret = new java.util.LinkedHashMap<>();
		ret.put("black-depth", 0);
		return ret;
	}

	protected void checkValid(java.util.Map<String, Object> properties) {
		Integer blackDepth = (Integer) properties.get("black-depth");
		if(isRed) {
			if((theLeft != null && theLeft.isRed) || (theRight != null && theRight.isRed))
				throw new IllegalStateException("Red node (" + this + ") has red children");
		} else {
			blackDepth = blackDepth + 1;
			properties.put("black-depth", blackDepth);
		}
		if(theLeft == null && theRight == null) {
			Integer leafBlackDepth = (Integer) properties.get("leaf-black-depth");
			if(leafBlackDepth == null) {
				properties.put("leaf-black-depth", leafBlackDepth);
			} else if(!leafBlackDepth.equals(blackDepth))
				throw new IllegalStateException("Different leaf black depths: " + leafBlackDepth + " and " + blackDepth);
		}
	}

	public RedBlackNode getRootNoCycles() {
		return getRootNoCycles(new java.util.LinkedHashSet<>());
	}

	private RedBlackNode getRootNoCycles(java.util.Set<RedBlackNode> visited) {
		if(theParent == null || !visited.add(this))
			return this;
		return theParent.getRootNoCycles(visited);
	}

	public RedBlackNode getLeft() {
		return theLeft;
	}

	public RedBlackNode getRight() {
		return theRight;
	}

	@Override
	public abstract int compareTo(RedBlackNode node);

	public boolean getSide() {
		if(theParent == null)
			return false;
		if(this == theParent.getLeft())
			return true;
		return false;
	}

	public RedBlackNode getChild(boolean left) {
		return left ? theLeft : theRight;
	}

	public RedBlackNode getSibling() {
		if(theParent.getLeft() == this)
			return theParent.getRight();
		else
			return theParent.getLeft();
	}

	public RedBlackNode find(Comparable<RedBlackNode> finder) {
		int compare = finder.compareTo(this);
		if(compare == 0)
			return this;
		RedBlackNode child = getChild(compare < 0);
		if(child != null)
			return child.find(finder);
		else
			return null;
	}

	public RedBlackNode findClosest(Comparable<RedBlackNode> finder, boolean lesser, boolean withExact) {
		return findClosest(finder, lesser, withExact, null);
	}

	private RedBlackNode findClosest(Comparable<RedBlackNode> finder, boolean lesser, boolean withExact, RedBlackNode found) {
		int compare = finder.compareTo(this);
		if(compare == 0 && withExact)
			return this;
		if(compare < 0 == lesser)
			found = this;
		RedBlackNode child = getChild(compare < 0);
		if(child != null)
			return child.findClosest(finder, lesser, withExact, found);
		else
			return found;
	}

	public int getSize() {
		int ret = 1;
		if(theLeft != null)
			ret += theLeft.getSize();
		if(theRight != null)
			ret += theRight.getSize();
		return ret;
	}

	@Override
	public RedBlackNode clone() {
		RedBlackNode ret;
		try {
			ret = (RedBlackNode) super.clone();
		} catch(CloneNotSupportedException e) {
			throw new IllegalStateException("Not cloneable");
		}
		if(ret.theLeft != null)
			ret.setChild(ret.theLeft.clone(), true);
		if(ret.theRight != null)
			ret.setChild(ret.theRight.clone(), false);
		return ret;
	}

	/**
	 * Sets this node's parent. This method should <b>NEVER</b> be called from outside of the {@link RedBlackNode} class.
	 *
	 * @param parent The parent for this node
	 * @return The node that was previously this node's parent, or null if it did not have a parent
	 */
	protected RedBlackNode setParent(RedBlackNode parent) {
		if(parent == this)
			throw new IllegalArgumentException("A tree node cannot be its own parent: " + parent);
		RedBlackNode oldParent = theParent;
		theParent = parent;
		return oldParent;
	}

	/**
	 * Sets one of this node's children. This method should <b>NEVER</b> be called from outside of the {@link RedBlackNode} class.
	 *
	 * @param child The new child for this node
	 * @param left Whether to set the left or the right child
	 * @return The child (or null) that was replaced as this node's left or right child
	 */
	protected RedBlackNode setChild(RedBlackNode child, boolean left) {
		if(child == this)
			throw new IllegalArgumentException("A tree node cannot have itself as a child: " + this + " (" + (left ? "left" : "right")
				+ ")");
		RedBlackNode oldChild;
		if(left) {
			oldChild = theLeft;
			theLeft = child;
		} else {
			oldChild = theRight;
			theRight = child;
		}
		if(child != null)
			child.setParent(this);
		return oldChild;
	}

	/**
	 * Sets this node's color. This method should <b>NEVER</b> be called from outside of the {@link RedBlackNode} class.
	 *
	 * @param red Whether this node will be red or black
	 */
	protected void setRed(boolean red) {
		isRed = red;
	}

	/**
	 * Replaces this node in the tree with the given node, orphaning this node.
	 *
	 * @param node The node to replace this node in the tree
	 */
	protected void replace(RedBlackNode node) {
		if(node != theLeft)
			node.setChild(theLeft, true);
		if(node != theRight)
			node.setChild(theRight, false);
		setChild(null, true);
		setChild(null, false);
		setRed(isRed);
		if(theParent != null) {
			theParent.setChild(node, getSide());
			setParent(null);
		} else
			node.setParent(theParent);
	}

	/**
	 * Causes this node to switch places in the tree with the given node. This method should <b>NEVER</b> be called from outside of the
	 * {@link RedBlackNode} class.
	 *
	 * @param node
	 */
	protected void switchWith(RedBlackNode node) {
		boolean thisRed = isRed;
		setRed(node.isRed);
		node.setRed(thisRed);
		RedBlackNode temp = theLeft;
		setChild(node.theLeft, true);
		node.setChild(temp, true);
		temp = theRight;
		setChild(node.theRight, false);
		node.setChild(temp, false);
		temp = theParent;
		setParent(node.theParent);
		node.setParent(temp);
	}

	protected RedBlackNode rotate(boolean left) {
		RedBlackNode oldChild = getChild(!left);
		RedBlackNode newChild = oldChild.getChild(left);
		RedBlackNode oldParent = getParent();
		boolean oldSide = getSide();
		oldChild.setChild(this, left);
		setChild(newChild, !left);
		if(oldParent != null)
			oldParent.setChild(oldChild, oldSide);
		else
			oldChild.setParent(null);
		return oldChild;
	}

	protected TreeOpResult add(RedBlackNode node, boolean replaceIfFound) {
		int compare = node.compareTo(this);
		if(compare == 0) {
			if(replaceIfFound) {
				replace(node);
				return new TreeOpResult(this, node, node);
			} else
				return new TreeOpResult(this, this, this);
		}
		return addOnSide(node, compare < 0, replaceIfFound);
	}

	private static final boolean DEBUG = false;

	protected TreeOpResult addOnSide(RedBlackNode node, boolean left, boolean replaceIfFound) {
		RedBlackNode child = getChild(left);
		TreeOpResult r;
		if(child == null) {
			setChild(node, left);
			child = node;
			r = new TreeOpResult(null, node, this);
		} else {
			r = child.add(node, replaceIfFound);
			if(r.theNewRoot == theParent) {
				return r;
			}
			r.theNewRoot = this;
		}

		if(!DEBUG) {
			// Rebalance after add
			if(theParent == null) {
				isRed = false; // Root is black
				return r;
			} else if(!isRed || !child.isRed) {
				// We're good, nothing to do
				return r;
			} else {
				boolean parentLeft = getSide();
				RedBlackNode uncle = getSibling(); // Get the other sibling
				if(uncle != null && uncle.isRed) {
					// Switch colors of this node, its parent, and the uncle
					setRed(false);
					uncle.setRed(false);
					theParent.setRed(true);
					return r;
				} else {
					RedBlackNode ret = this;
					if(parentLeft != left) { // New node on right
						ret = rotate(parentLeft);
					}
					ret.setRed(false);
					ret.getParent().setRed(true);
					r.theNewRoot = ret.theParent.rotate(!parentLeft);
					return r;
				}
			}
		} else {
			r.theNewRoot = node;
			return r;
		}
	}

	protected RedBlackNode delete() {
		if(theLeft != null && theRight != null) {
			RedBlackNode successor = getClosest(false);
			switchWith(successor);
			// Now we've switched locations with successor, so we have either 0 or 1 children and can continue with delete
		}
		RedBlackNode replacement = null;
		if(theLeft != null)
			replacement = theLeft;
		else if(theRight != null)
			replacement = theRight;

		if(replacement != null) {
			boolean oldRed = replacement.isRed;
			replace(replacement);
			replacement.setRed(oldRed);
			setParent(null);
			setChild(null, true);
			setChild(null, false);
			if(!isRed)
				return fixAfterDeletion(replacement);
			else
				return replacement.getRoot();
		} else if(theParent == null)
			return null;
		else {
			RedBlackNode ret = fixAfterDeletion(this);
			theParent.setChild(null, getSide());
			setParent(null);
			return ret;
		}
	}

	public RedBlackNode getClosest(boolean left) {
		RedBlackNode child = getChild(left);
		if(child != null) {
			RedBlackNode next = child.getChild(!left);
			while(next != null) {
				child = next;
				next = next.getChild(!left);
			}
			return child;
		} else {
			RedBlackNode parent = theParent;
			child = this;
			while(parent != null && parent.getChild(left) == child) {
				child = parent;
				parent = parent.theParent;
			}
			return parent;
		}
	}

	@Override
	public String toString() {
		return super.toString() + " (" + (isRed ? "red" : "black") + ")";
	}

	/** This is not used, but here for reference. It is the rebalancing code from {@link java.util.TreeMap}, refactored for RedBlackTree. */
	private static RedBlackNode fixAfterInsertion(RedBlackNode root, RedBlackNode x) {
		while(x != null && x != root && x.getParent().getParent() != null && x.getParent().isRed()) {
			boolean parentLeft = x.getParent().getSide();
			RedBlackNode y = x.getParent().getSibling();
			if(y != null && y.isRed()) {
				System.out.println("Case 1");
				x.getParent().setRed(false);
				y.setRed(false);
				x.getParent().getParent().setRed(true);
				x = x.getParent().getParent();
			} else {
				if(parentLeft != x.getSide()) {
					System.out.println("Case 2, rotate " + (parentLeft ? "left" : "right"));
					x = x.getParent();
					x.rotate(parentLeft);
				}
				System.out.println("Case 3, rotate " + (parentLeft ? "right" : "left"));
				x.getParent().setRed(false);
				x.getParent().getParent().setRed(true);
				x.getParent().getParent().rotate(!parentLeft);
			}
			root = root.getRoot();
		}
		root.setRed(false);
		return root;
	}

	private static RedBlackNode fixAfterDeletion(RedBlackNode node) {
		while(node.theParent != null && !node.isRed) {
			boolean parentLeft = node.getSide();
			RedBlackNode sib = node.theParent.getChild(!parentLeft);

			if(sib.isRed) {
				sib.setRed(false);
				node.theParent.setRed(true);
				node.theParent.rotate(parentLeft);
				sib = node.theParent.getChild(!parentLeft);
			}
			if((sib.theLeft == null || !sib.theLeft.isRed) && (sib.theRight == null || !sib.theRight.isRed)) {
				sib.setRed(true);
				node = node.theParent;
			} else {
				if(!sib.getChild(!parentLeft).isRed) {
					sib.getChild(parentLeft).setRed(false);
					sib.setRed(true);
					sib.rotate(!parentLeft);
					sib = node.theParent.getChild(!parentLeft);
				}
				sib.setRed(node.theParent.isRed);
				node.theParent.setRed(false);
				sib.getChild(!parentLeft).setRed(false);
				node.theParent.rotate(parentLeft);
				node = node.getRoot();
			}
		}

		node.setRed(false);
		return node.getRoot();
	}

	public static String print(RedBlackNode tree) {
		StringBuilder ret = new StringBuilder();
		print(tree, ret, 0);
		return ret.toString();
	}

	public static void print(RedBlackNode tree, StringBuilder str, int indent) {
		if(tree == null) {
			for(int i = 0; i < indent; i++)
				str.append('\t');
			str.append(tree).append('\n');
			return;
		}

		RedBlackNode right = tree.getRight();
		if(right != null)
			print(right, str, indent + 1);

		for(int i = 0; i < indent; i++)
			str.append('\t');
		str.append(tree).append('\n');

		RedBlackNode left = tree.getLeft();
		if(left != null)
			print(left, str, indent + 1);
	}

	public static abstract class ValuedRedBlackNode<E> extends RedBlackNode {
		private final E theValue;

		public ValuedRedBlackNode(E value) {
			theValue = value;
		}

		public E getValue() {
			return theValue;
		}

		protected abstract int compare(E o1, E o2);

		protected abstract ValuedRedBlackNode<E> createNode(E value);

		@Override
		public int compareTo(RedBlackNode node) {
			return compare(getValue(), ((ValuedRedBlackNode<E>) node).getValue());
		}

		protected TreeOpResult add(E value, boolean replaceIfFound) {
			ValuedRedBlackNode<E> node = createNode(value);
			return add(node, replaceIfFound);
		}

		public ValuedRedBlackNode<E> findValue(E value) {
			return (ValuedRedBlackNode<E>) find(node -> compare(value, ((ValuedRedBlackNode<E>) node).getValue()));
		}

		public ValuedRedBlackNode<E> findClosestValue(E value, boolean lesser, boolean withEqual) {
			return (ValuedRedBlackNode<E>) findClosest(node -> compare(value, ((ValuedRedBlackNode<E>) node).getValue()), lesser, withEqual);
		}

		@Override
		public String toString() {
			return String.valueOf(theValue) + " (" + (isRed() ? "red" : "black") + ")";
		}
	}

	public static class ComparableValuedRedBlackNode<E extends Comparable<E>> extends ValuedRedBlackNode<E> {
		public ComparableValuedRedBlackNode(E value) {
			super(value);
		}

		@Override
		public int compareTo(RedBlackNode o) {
			return getValue().compareTo(((ComparableValuedRedBlackNode<E>) o).getValue());
		}

		@Override
		protected ComparableValuedRedBlackNode<E> createNode(E value) {
			return new ComparableValuedRedBlackNode<>(value);
		}

		@Override
		protected int compare(E o1, E o2) {
			return o1.compareTo(o2);
		}

		public static <E extends Comparable<E>> ComparableValuedRedBlackNode<E> valueOf(E value) {
			return new ComparableValuedRedBlackNode<>(value);
		}
	}


	public static void main(String [] args) {
		test(ComparableValuedRedBlackNode.valueOf("a"), alphaBet('q'));
	}

	protected static final Iterable<String> alphaBet(char last) {
		return () -> {
			return new Iterator<String>() {
				private char theNext = 'a';

				@Override
				public boolean hasNext() {
					return theNext <= last;
				}

				@Override
				public String next() {
					String ret = "" + theNext;
					theNext++;
					return ret;
				}
			};
		};
	}

	public static <T> void test(ValuedRedBlackNode<T> tree, Iterable<T> nodes) {
		ValuedRedBlackNode<T> node;
		Iterator<T> iter = nodes.iterator();
		iter.next(); // Skip the first value, assuming that's what's in the tree
		System.out.println(print(tree));
		System.out.println(" ---- ");
		while(iter.hasNext()) {
			T value = iter.next();
			System.out.println("Adding " + value);
			node = (ValuedRedBlackNode<T>) tree.add(value, false).getNewRoot();
			if(DEBUG)
				tree = (ValuedRedBlackNode<T>) fixAfterInsertion(tree, node);
			else
				tree = node;
			System.out.println(print(tree));
			tree.checkValid();
			System.out.println(" ---- ");
		}
		System.out.println(" ---- \n ---- \nDeleting:");

		iter = nodes.iterator();
		while(iter.hasNext()) {
			T value = iter.next();
			System.out.println("Deleting " + value);
			tree = (ValuedRedBlackNode<T>) tree.findValue(value).delete();
			System.out.println(print(tree));
			if(tree != null)
				tree.checkValid();
			System.out.println(" ---- ");
		}
	}
}
