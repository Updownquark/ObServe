package org.observe.util.tree;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class NumberedRedBlackNode<E> extends CountedRedBlackNode<E> {
	private long theRangeMin;
	private long theRangeMax;

	public NumberedRedBlackNode(E value) {
		super(value);
		theRangeMin = Long.MIN_VALUE;
		theRangeMax = Long.MAX_VALUE;
	}

	@Override
	protected NumberedRedBlackNode<E> createNode(E value) {
		return new NumberedRedBlackNode<>(value);
	}

	@Override
	protected int compare(E o1, E o2) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int compareTo(RedBlackNode node) {
		long diff = theRangeMin = ((NumberedRedBlackNode<?>) node).theRangeMin;
		if(diff < 0)
			return -1;
		else if(diff > 0)
			return 1;
		else
			return 0;
	}

	@Override
	protected void replace(RedBlackNode node) {
		super.replace(node);
		NumberedRedBlackNode<E> nrbn = (NumberedRedBlackNode<E>) node;
		nrbn.theRangeMin = theRangeMin;
		nrbn.theRangeMax = theRangeMax;
	}

	@Override
	protected void switchWith(RedBlackNode node) {
		super.switchWith(node);
		NumberedRedBlackNode<E> nrbn = (NumberedRedBlackNode<E>) node;
		long temp;
		temp = theRangeMin;
		theRangeMin = nrbn.theRangeMin;
		nrbn.theRangeMin = temp;
		temp = theRangeMax;
		theRangeMax = nrbn.theRangeMax;
		nrbn.theRangeMax = temp;
	}

	@Override
	protected CountedRedBlackNode<E> rotate(boolean left) {
		return super.rotate(left);
		// TODO Switch around the ranges
	}

	@Override
	protected RedBlackNode setChild(RedBlackNode child, boolean left) {
		if(getChild(left) == null) {
			// TODO Adding new child. Set its range, borrowing from ancestors if needed
		}
		return super.setChild(child, left);
	}

	public static class RedBlackTreeList<E> implements List<E> {
		private NumberedRedBlackNode<E> theRoot;

		@Override
		public int size() {
			return theRoot == null ? 0 : theRoot.getSize();
		}

		@Override
		public boolean isEmpty() {
			return theRoot == null;
		}

		@Override
		public boolean contains(Object o) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public Iterator<E> iterator() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Object [] toArray() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public <T> T [] toArray(T [] a) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public boolean add(E e) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean remove(Object o) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean addAll(Collection<? extends E> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean addAll(int index, Collection<? extends E> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void clear() {
			theRoot = null;
		}

		@Override
		public E get(int index) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public E set(int index, E element) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void add(int index, E element) {
			// TODO Auto-generated method stub

		}

		@Override
		public E remove(int index) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int indexOf(Object o) {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public int lastIndexOf(Object o) {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public ListIterator<E> listIterator() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public ListIterator<E> listIterator(int index) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public List<E> subList(int fromIndex, int toIndex) {
			// TODO Auto-generated method stub
			return null;
		}
	}
}
