package org.observe.collect;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;

/**
 * An extension of ObservableList that implements some of the redundant methods and throws UnsupportedOperationExceptions for modifications.
 * Mostly copied from {@link AbstractList}.
 *
 * @param <E> The type of element in the list
 */
public interface PartialListImpl<E> extends PartialCollectionImpl<E>, ObservableList<E> {
	@Override
	default boolean contains(Object o) {
		return ObservableList.super.contains(o);
	}

	@Override
	default boolean containsAll(Collection<?> coll) {
		return ObservableList.super.containsAll(coll);
	}

	@Override
	default boolean retainAll(Collection<?> coll) {
		return PartialCollectionImpl.super.retainAll(coll);
	}

	@Override
	default boolean removeAll(Collection<?> coll) {
		return PartialCollectionImpl.super.removeAll(coll);
	}

	@Override
	default boolean remove(Object o) {
		return PartialCollectionImpl.super.remove(o);
	}

	@Override
	default boolean add(E e) {
		add(size(), e);
		return true;
	}

	@Override
	default E set(int index, E element) {
		throw new UnsupportedOperationException();
	}

	@Override
	default void add(int index, E element) {
		throw new UnsupportedOperationException();
	}

	@Override
	default E remove(int index) {
		throw new UnsupportedOperationException();
	}

	@Override
	default int indexOf(Object o) {
		ListIterator<E> it = listIterator();
		if(o == null) {
			while(it.hasNext())
				if(it.next() == null)
					return it.previousIndex();
		} else {
			while(it.hasNext())
				if(o.equals(it.next()))
					return it.previousIndex();
		}
		return -1;
	}

	@Override
	default int lastIndexOf(Object o) {
		ListIterator<E> it = listIterator(size());
		if(o == null) {
			while(it.hasPrevious())
				if(it.previous() == null)
					return it.nextIndex();
		} else {
			while(it.hasPrevious())
				if(o.equals(it.previous()))
					return it.nextIndex();
		}
		return -1;
	}

	@Override
	default void clear() {
		removeRange(0, size());
	}

	@Override
	default boolean addAll(Collection<? extends E> c) {
		return addAll(0, c);
	}

	@Override
	default boolean addAll(int index, Collection<? extends E> c) {
		if(index < 0 || index > size())
			throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
		boolean modified = false;
		for(E e : c) {
			add(index++, e);
			modified = true;
		}
		return modified;
	}

	@Override
	default Iterator<E> iterator() {
		return listIterator();
	}

	/**
	 * Removes from this list all of the elements whose index is between {@code fromIndex}, inclusive, and {@code toIndex}, exclusive.
	 * Shifts any succeeding elements to the left (reduces their index). This call shortens the list by {@code (toIndex - fromIndex)}
	 * elements. (If {@code toIndex==fromIndex}, this operation has no effect.)
	 *
	 * <p>
	 * This method is called by the {@code clear} operation on this list and its subLists. Overriding this method to take advantage of the
	 * internals of the list implementation can <i>substantially</i> improve the performance of the {@code clear} operation on this list and
	 * its subLists.
	 *
	 * <p>
	 * This implementation gets a list iterator positioned before {@code fromIndex}, and repeatedly calls {@code ListIterator.next} followed
	 * by {@code ListIterator.remove} until the entire range has been removed. <b>Note: if {@code ListIterator.remove} requires linear time,
	 * this implementation requires quadratic time.</b>
	 *
	 * @param fromIndex index of first element to be removed
	 * @param toIndex index after last element to be removed
	 */
	default void removeRange(int fromIndex, int toIndex) {
		ListIterator<E> it = listIterator(fromIndex);
		for(int i = 0, n = toIndex - fromIndex; i < n; i++) {
			it.next();
			it.remove();
		}
	}
}