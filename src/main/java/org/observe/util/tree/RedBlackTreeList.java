package org.observe.util.tree;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.function.Function;

public class RedBlackTreeList<N extends CountedRedBlackNode<E>, E> extends AbstractList<E> {
	private final Function<E, N> theNodeCreator;

	private N theRoot;

	public RedBlackTreeList(Function<E, N> nodeCreator) {
		theNodeCreator = nodeCreator;
	}

	protected N createNode(E value) {
		return theNodeCreator.apply(value);
	}

	public N getNodeAt(int index) {
		return getNodeAt(theRoot, index, 0);
	}

	protected N getNodeAt(N node, int index, int passed) {
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
		N root = theRoot;
		if(root == null)
			return java.util.Collections.EMPTY_LIST.iterator();
		return iterator(getNodeAt(0), true);
	}

	public Iterator<E> iterator(N start, boolean forward) {
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

	public Iterator<N> nodeIterator(N start, boolean forward) {
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
				theNextNode = (N) nextNode.getClosest(forward);
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

	protected N addBefore(E element, N before) {
	}

	protected N addAfter(E element, N after) {
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
