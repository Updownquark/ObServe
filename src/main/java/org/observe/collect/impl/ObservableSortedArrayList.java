package org.observe.collect.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.ObservableValue;
import org.observe.collect.CollectionSession;
import org.qommons.Transactable;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

/**
 * A list that maintains its order via a comparator. Different from a sorted set in that multiple equivalent elements may be stored in an
 * ordered list. Since this collection maintains its own order, certain operations using indexes may have behavior that breaks the
 * {@link List} contract.
 *
 * @param <E> The type of elements in this collection
 */
public class ObservableSortedArrayList<E> extends ObservableArrayList<E> {
	private final Comparator<? super E> theCompare;

	/**
	 * Creates the list
	 *
	 * @param type The type of elements for this list
	 * @param compare The comparator by which to sort this list's elements
	 */
	public ObservableSortedArrayList(TypeToken<E> type, Comparator<? super E> compare) {
		super(type);
		theCompare = compare;
	}

	/**
	 * This constructor is for specifying some of the internals of the list.
	 *
	 * @param type The type of elements for this list
	 * @param lock The lock for this list to use
	 * @param session The session for this list to use (see {@link #getSession()})
	 * @param sessionController The controller for the session. May be null, in which case the transactional methods in this collection will
	 *        not actually create transactions.
	 * @param compare The comparator by which to sort this list's elements
	 */
	public ObservableSortedArrayList(TypeToken<E> type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session,
		Transactable sessionController, Comparator<? super E> compare) {
		super(type, lock, session, sessionController);
		theCompare = compare;
	}

	@Override
	public boolean add(E e) {
		try (Transaction t = lock(true, null)) {
			int idx;
			// Add some optimizations for adding values in the right order
			if (isEmpty())
				idx = 0;
			else if (theCompare.compare(get(size() - 1), e) <= 0)
				idx = size();
			else if (theCompare.compare(get(0), e) > 0)
				idx = 0;
			else
				idx = Collections.binarySearch(this, e, theCompare);
			if (idx < 0)
				super.add(-(idx + 1), e);
			else {
				while (idx < size() && theCompare.compare(e, get(idx)) <= 0)
					idx++;
				super.add(idx, e);
			}
		}
		return true;
	}

	@Override
	public void add(int index, E element) {
		add(element);
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		boolean ret = false;
		for (E value : c)
			ret |= add(value);
		return ret;
	}

	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		boolean ret = false;
		for (E value : c)
			ret |= add(value);
		return ret;
	}

	@Override
	public E set(int index, E element) {
		E ret;
		try (Transaction t = lock(true, null)) {
			E current = get(index);
			if (current == element) {
				ret = element;
				if (!updated(index))
					super.set(index, element);
			} else {
				ret = remove(index);
				add(element);
			}
		}
		return ret;
	}

	/**
	 * @param index The index of the value that has changed, which may require resorting
	 * @return Whether a resort operation was performed
	 */
	public boolean updated(int index) {
		if (index > 0 && theCompare.compare(get(index - 1), get(index)) > 0) {
			add(remove(index));
			return true;
		} else if (index < size() - 1 && theCompare.compare(get(index), get(index + 1)) > 0) {
			add(remove(index));
			return true;
		} else
			return false;
	}
}
