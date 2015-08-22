package org.observe.collect;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * A partial implementation of List which also provides the {@link #removeRange(int, int)} method.
 *
 * @param <E> The type of elements in the list
 */
public interface RRList<E> extends List<E> {
	@Override
	default Iterator<E> iterator() {
		return listIterator();
	}

	@Override
	default ListIterator<E> listIterator() {
		return listIterator(0);
	}

	@Override
	default boolean add(E e) {
		add(size(), e);
		return true;
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
		// try (Transaction t = lock(true, null)) {
		ListIterator<E> it = listIterator(fromIndex);
		for(int i = 0, n = toIndex - fromIndex; i < n; i++) {
			it.next();
			it.remove();
		}
		// }
	}

	@Override
	default void clear() {
		removeRange(0, size());
	}
}
