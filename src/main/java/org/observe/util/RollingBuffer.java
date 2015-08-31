package org.observe.util;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;

/**
 * A rolling buffer that adds to the end and drops items off of the beginning when the size exceeds its capacity
 *
 * @param <E> The type of element in the buffer
 */
public class RollingBuffer<E> extends AbstractCollection<E> {
	private Node<E> theFirst;
	private Node<E> theLast;

	private int theSize;
	private int theCapacity;
	private int theTotal;

	/** @param capacity The capacity for this buffer */
	public RollingBuffer(int capacity) {
		setCapacity(capacity);
	}

	/** @return This buffer's capacity, i.e. the number of items it will accept before new items cause old ones to be dropped */
	public int getCapacity() {
		return theCapacity;
	}

	/**
	 * @param cap The new capacity for this buffer. If this is smaller than the current size, this will cause some of the oldest items to be
	 *            dropped
	 */
	public void setCapacity(int cap) {
		if(cap <= 0)
			throw new IllegalArgumentException("Capacity must be positive");
		theCapacity = cap;
		while(theSize > theCapacity) {
			theFirst = theFirst.theNext;
			theSize--;
		}
	}

	/** @return The total number of items that have <b>ever</b> been added to this buffer */
	public int getTotal() {
		return theTotal;
	}

	@Override
	public int size() {
		return theSize;
	}

	@Override
	public Iterator<E> iterator() {
		return new Iterator<E>() {
			private Node<E> thePrePassed = null;

			private Node<E> thePassed = null;
			private Node<E> theNext = theFirst;

			@Override
			public boolean hasNext() {
				return theNext != null;
			}

			@Override
			public E next() {
				if(theNext == null)
					throw new java.util.NoSuchElementException();
				E value = theNext.theValue;
				thePrePassed = thePassed;
				thePassed = theNext;
				theNext = theNext.theNext;
				return value;
			}

			@Override
			public void remove() {
				if(thePassed == null)
					throw new IllegalStateException("remove() may only be called once after next()");
				thePrePassed.theNext = theNext;
				thePassed = null;
				theSize--;
			}
		};
	}

	@Override
	public boolean add(E e) {
		Node<E> newNode = new Node<>(e);
		if(theFirst != null) {
			theLast.theNext = newNode;
		} else
			theFirst = newNode;
		theLast = newNode;
		if(theSize < theCapacity)
			theSize++;
		else
			theFirst = theFirst.theNext;
		theTotal++;
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		Iterator<? extends E> iter = c.iterator();
		{ // This is just optimizing
			int toRemove = c.size() - theCapacity;
			for(int i = 0; i < toRemove; i++)
				iter.next();
			if(toRemove >= 0)
				clear();
		}
		while(iter.hasNext()) {
			add(iter.next());
		}
		return !c.isEmpty();
	}

	@Override
	public void clear() {
		theSize = 0;
		theFirst = theLast = null;
	}

	private static final class Node<E> {
		final E theValue;
		Node<E> theNext;

		Node(E value) {
			theValue = value;
		}
	}
}
