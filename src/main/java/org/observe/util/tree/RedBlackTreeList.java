package org.observe.util.tree;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.function.Function;

/**
 * A list backed by a binary red/black tree structure. Tree lists are O(log(n)) for get(index) and all modifications; constant time-from a
 * {@link #listIterator() list iterator}.
 *
 * @param <N> The sub-type of {@link CountedRedBlackNode} used to store the data
 * @param <E> The type of values in the list
 */
public class RedBlackTreeList<N extends CountedRedBlackNode<E>, E> extends AbstractList<E> {
	private final Function<E, N> theNodeCreator;

	private N theRoot;

	/** @param nodeCreator The function to create nodes for the list */
	public RedBlackTreeList(Function<E, N> nodeCreator) {
		theNodeCreator = nodeCreator;
	}

	/** @return The root of this list's tree structure */
	public N getRoot() {
		return theRoot;
	}

	/**
	 * @param value The value to create the node for
	 * @return The new node for the value
	 */
	protected N createNode(E value) {
		return theNodeCreator.apply(value);
	}

	/**
	 * @param index The index to get the node at
	 * @return The node at the given index
	 */
	public N getNodeAt(int index) {
		return getNodeAt(theRoot, index, 0);
	}

	private N getNodeAt(N node, int index, int passed) {
		if(node == null)
			throw new IndexOutOfBoundsException((passed + index) + " of " + CountedRedBlackNode.size(theRoot));
		int leftCount = CountedRedBlackNode.size(node.getLeft());
		if(index < leftCount)
			return getNodeAt((N) node.getLeft(), index, passed);
		else if(index == leftCount)
			return node;
		else
			return getNodeAt((N) node.getRight(), index - leftCount - 1, passed + leftCount + 1);
	}

	/** @return The last node in this tree */
	public N getLastNode() {
		N ret = theRoot;
		while(ret != null && ret.getRight() != null)
			ret = (N) ret.getRight();
		return ret;
	}

	@Override
	public int size() {
		return CountedRedBlackNode.size(theRoot);
	}

	@Override
	public boolean isEmpty() {
		return theRoot == null;
	}

	@Override
	public E get(int index) {
		N node = getNodeAt(index);
		return node.getValue();
	}

	@Override
	public Iterator<E> iterator() {
		return iterator(true);
	}

	/**
	 * @param forward Whether to iterate forward through the list or backward
	 * @return The iterator
	 */
	public Iterator<E> iterator(boolean forward) {
		N root = theRoot;
		if(root == null)
			return java.util.Collections.EMPTY_LIST.iterator();
		return iterator(forward ? getNodeAt(0) : getLastNode(), forward);
	}

	/**
	 * @param forward Whether to iterate forward through the list or backward
	 * @return The node iterator
	 */
	public Iterator<N> nodeIterator(boolean forward) {
		N root = theRoot;
		if(root == null)
			return java.util.Collections.EMPTY_LIST.iterator();
		return nodeIterator(getNodeAt(0), forward);
	}

	private Iterator<E> iterator(N start, boolean forward) {
		return new Iterator<E>() {
			private final Iterator<N> backing = nodeIterator(start, forward);

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

	private Iterator<N> nodeIterator(N start, boolean forward) {
		return new Iterator<N>() {
			private N theLastNode = null;

			private N theNextNode = start;

			@Override
			public boolean hasNext() {
				return theNextNode != null;
			}

			@Override
			public N next() {
				N nextNode = theNextNode;
				if(nextNode == null)
					throw new java.util.NoSuchElementException();
				theLastNode = nextNode;
				theNextNode = (N) nextNode.getClosest(!forward);
				return theLastNode;
			}

			@Override
			public void remove() {
				if(theLastNode == null)
					throw new IllegalStateException("remove() may only be called after next() and only once after each call to next()");
				theRoot = (N) theLastNode.delete();
				theLastNode = null;
			}
		};
	}

	@Override
	public boolean add(E e) {
		N node = getLastNode();
		addAfter(e, node);
		return true;
	}

	@Override
	public void add(int index, E element) {
		if(theRoot == null && index == 0) {
			theRoot = createNode(element);
			return;
		}
		N node = getNodeAt(index);
		addBefore(element, node);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		N node = getLastNode();
		for(E o : c)
			node = addAfter(o, node);
		return !c.isEmpty();
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		if(index == size())
			return addAll(c);
		N node = getNodeAt(index);
		boolean first = false;
		for(E o : c) {
			if(first) {
				node = addBefore(o, node);
				first = false;
			} else
				node = addAfter(o, node);
		}
		return !c.isEmpty();
	}

	/**
	 * @param element the element to add
	 * @param before The node to add the element before
	 * @return The new node
	 */
	public N addBefore(E element, N before) {
		N newNode = createNode(element);
		N left = (N) before.getLeft();
		RedBlackNode.TreeOpResult result;
		if(left == null)
			result = before.addOnSide(newNode, true, true);
		else {
			while(left.getRight() != null)
				left = (N) left.getRight();
			result = left.addOnSide(newNode, false, true);
		}
		theRoot = (N) result.getNewRoot();
		return newNode;
	}

	/**
	 * @param element the element to add
	 * @param after The node to add the element after
	 * @return The new node
	 */
	public N addAfter(E element, N after) {
		N newNode = createNode(element);
		if(after == null) {
			if(theRoot == null) {
				theRoot = newNode;
				newNode.setRed(false);
			} else {
				N farLeft = theRoot;
				while(farLeft.getChild(true) != null)
					farLeft = (N) farLeft.getChild(true);
				RedBlackNode.TreeOpResult result = farLeft.addOnSide(newNode, true, true);
				theRoot = (N) result.getNewRoot();
			}
		} else {
			N right = (N) after.getRight();
			RedBlackNode.TreeOpResult result;
			if(right == null)
				result = after.addOnSide(newNode, false, true);
			else {
				while(right.getLeft() != null)
					right = (N) right.getLeft();
				result = right.addOnSide(newNode, true, true);
			}
			theRoot = (N) result.getNewRoot();
		}
		return newNode;
	}

	@Override
	public E remove(int index) {
		N node = getNodeAt(index);
		theRoot = (N) node.delete();
		return node.getValue();
	}

	@Override
	public void clear() {
		theRoot = null;
	}

	@Override
	public E set(int index, E element) {
		N node = getNodeAt(index);
		E old = node.getValue();
		node.replace(createNode(element));
		return old;
	}

	@Override
	public ListIterator<E> listIterator(int index) {
		N start;
		boolean startingAfter;
		if(index==size()){
			start = getLastNode();
			startingAfter = true;
		} else {
			start=getNodeAt(index);
			startingAfter = false;
		}
		return new ListIterator<E>() {
			private N theLastNode;
			private N theNextNode;
			private boolean calledNextMostRecently;

			private boolean hasCalledAnything;

			{
				if(startingAfter) {
					theLastNode = start;
					theNextNode = (N) theLastNode.getClosest(false);
				} else {
					theNextNode = start;
					theLastNode = (N) theNextNode.getClosest(true);
				}
			}

			@Override
			public boolean hasNext() {
				return theNextNode != null;
			}

			@Override
			public E next() {
				N node = theNextNode;
				if(node == null)
					throw new java.util.NoSuchElementException();
				theNextNode = (N) theNextNode.getClosest(false);
				calledNextMostRecently = true;
				hasCalledAnything = true;
				theLastNode = node;
				return node.getValue();
			}

			@Override
			public boolean hasPrevious() {
				return theLastNode != null;
			}

			@Override
			public E previous() {
				N node = theLastNode;
				if(node == null)
					throw new java.util.NoSuchElementException();
				theLastNode = (N) theLastNode.getClosest(true);
				calledNextMostRecently = false;
				hasCalledAnything = true;
				theNextNode = node;
				return node.getValue();
			}

			@Override
			public int nextIndex() {
				if(theNextNode == null)
					return size();
				return theNextNode.getIndex();
			}

			@Override
			public int previousIndex() {
				if(theLastNode == null)
					return 0;
				return theLastNode.getIndex();
			}

			@Override
			public void remove() {
				if(!hasCalledAnything)
					throw new IllegalStateException("remove() must be called after next() or previous()");
				if(calledNextMostRecently) {
					if(theLastNode == null)
						throw new IllegalStateException("remove() cannot be called twice in a row");
					theRoot = (N) theLastNode.delete();
					theLastNode = (N) theLastNode.getClosest(true);
				} else {
					if(theNextNode == null)
						throw new IllegalStateException("remove() cannot be called twice in a row");
					theRoot = (N) theNextNode.delete();
					theNextNode = (N) theNextNode.getClosest(true);
				}
			}

			@Override
			public void set(E e) {
				if(!hasCalledAnything)
					throw new IllegalStateException("set() must be called after next() or previous()");
				if(calledNextMostRecently) {
					N newNode = createNode(e);
					theLastNode.replace(newNode);
					theLastNode = newNode;
				} else {
					N newNode = createNode(e);
					theNextNode.replace(newNode);
					theNextNode = newNode;
				}
			}

			@Override
			public void add(E e) {
				if(!hasCalledAnything)
					throw new IllegalStateException("set() must be called after next() or previous()");
				if(calledNextMostRecently) {
					theLastNode = addAfter(e, theLastNode);
				} else {
					theNextNode = addBefore(e, theNextNode);
				}
			}
		};
	}
}
