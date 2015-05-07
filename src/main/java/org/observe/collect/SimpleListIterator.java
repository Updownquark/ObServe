package org.observe.collect;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.NoSuchElementException;

public class SimpleListIterator<E> implements java.util.ListIterator<E> {
	private final List<E> theList;
	/**
	 * Index of element to be returned by subsequent call to next.
	 */
	int cursor = 0;

	/**
	 * Index of element returned by most recent call to next or
	 * previous.  Reset to -1 if this element is deleted by a call
	 * to remove.
	 */
	int lastRet = -1;

	SimpleListIterator(List<E> list, int index) {
		theList=list;
		cursor = index;
	}

	@Override
	public boolean hasNext() {
		return cursor != theList.size();
	}

	@Override
	public E next() {
		try {
			int i = cursor;
			E next = theList.get(i);
			lastRet = i;
			cursor = i + 1;
			return next;
		} catch (IndexOutOfBoundsException e) {
			throw new NoSuchElementException();
		}
	}

	@Override
	public void remove() {
		if (lastRet < 0)
			throw new IllegalStateException();

		try {
			theList.remove(lastRet);
			if (lastRet < cursor)
				cursor--;
			lastRet = -1;
		} catch (IndexOutOfBoundsException e) {
			throw new ConcurrentModificationException();
		}
	}

	@Override
	public boolean hasPrevious() {
		return cursor != 0;
	}

	@Override
	public E previous() {
		try {
			int i = cursor - 1;
			E previous = theList.get(i);
			lastRet = cursor = i;
			return previous;
		} catch (IndexOutOfBoundsException e) {
			throw new NoSuchElementException();
		}
	}

	@Override
	public int nextIndex() {
		return cursor;
	}

	@Override
	public int previousIndex() {
		return cursor-1;
	}

	@Override
	public void set(E e) {
		if (lastRet < 0)
			throw new IllegalStateException();

		try {
			theList.set(lastRet, e);
		} catch (IndexOutOfBoundsException ex) {
			throw new ConcurrentModificationException();
		}
	}

	@Override
	public void add(E e) {
		try {
			int i = cursor;
			theList.add(i, e);
			lastRet = -1;
			cursor = i + 1;
		} catch (IndexOutOfBoundsException ex) {
			throw new ConcurrentModificationException();
		}
	}
}