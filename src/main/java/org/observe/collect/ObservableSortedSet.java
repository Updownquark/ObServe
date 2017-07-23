package org.observe.collect;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.ImmutableIterator;
import org.qommons.collect.TransactableSortedSet;

/**
 * A sorted set whose content can be observed
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSortedSet<E> extends ObservableSet<E>, TransactableSortedSet<E>, BetterSortedSet<E> {
	@Override
	default ImmutableIterator<E> iterator() {
		return ObservableSet.super.iterator();
	}

	@Override
	default ElementSpliterator<E> spliterator() {
		return ObservableSet.super.spliterator();
	}

	@Override
	default int indexOf(Object value) {
		return BetterSortedSet.super.indexOf(value);
	}

	@Override
	default int lastIndexOf(Object value) {
		return BetterSortedSet.super.lastIndexOf(value);
	}

	@Override
	default boolean contains(Object o) {
		return ObservableSet.super.contains(o);
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		return ObservableSet.super.containsAll(c);
	}

	@Override
	default boolean remove(Object c) {
		return ObservableSet.super.remove(c);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return ObservableSet.super.removeAll(c);
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return ObservableSet.super.retainAll(c);
	}

	@Override
	abstract void clear();

	@Override
	default ElementSpliterator<E> spliterator(int index) {
		return BetterSortedSet.super.spliterator(index);
	}

	@Override
	default E lower(E e) {
		return BetterSortedSet.super.lower(e);
	}

	@Override
	default E floor(E e) {
		return BetterSortedSet.super.floor(e);
	}

	@Override
	default E ceiling(E e) {
		return BetterSortedSet.super.ceiling(e);
	}

	@Override
	default E higher(E e) {
		return BetterSortedSet.super.higher(e);
	}

	@Override
	default E first() {
		return BetterSortedSet.super.first();
	}

	@Override
	default E last() {
		return BetterSortedSet.super.last();
	}

	@Override
	default boolean forValue(Comparable<? super E> search, Consumer<? super E> onValue, boolean up) {
		return BetterSortedSet.super.forValue(search, onValue, up);
	}

	/**
	 * Returns a value at or adjacent to another value
	 *
	 * @param search The search to find the target value
	 * @param up Whether to get the closest value greater or less than the given value
	 * @return An observable value with the result of the operation
	 */
	ObservableValue<E> observeRelative(Comparable<? super E> search, boolean up);

	@Override
	default E relative(Comparable<? super E> search, boolean up) {
		return observeRelative(search, up).get();
	}

	@Override
	default E pollLast() {
		return ObservableSet.super.pollLast();
	}

	@Override
	default E pollFirst() {
		return ObservableSet.super.pollFirst();
	}

	@Override
	default ObservableSortedSet<E> reverse() {
		return new ObservableSortedSetImpl.ReversedSortedSet<>(this);
	}

	@Override
	default ObservableSortedSet<E> descendingSet() {
		return reverse();
	}

	@Override
	default Iterator<E> descendingIterator() {
		return reverse().iterator();
	}

	/**
	 * A sub-set of this set. Like {@link #subSet(Object, boolean, Object, boolean)}, but may be reversed.
	 *
	 * @param fromElement The minimum bounding element for the sub set
	 * @param fromInclusive Whether the minimum bound will be included in the sub set (if present in this set)
	 * @param toElement The maximum bounding element for the sub set
	 * @param toInclusive Whether the maximum bound will be included in the sub set (if present in this set)
	 * @return The sub set
	 */
	@Override
	default ObservableSortedSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return subSet(v -> {
			int compare = comparator().compare(fromElement, v);
			if (!fromInclusive && compare == 0)
				compare = 1;
			return compare;
		}, v -> {
			int compare = comparator().compare(toElement, v);
			if (!toInclusive && compare == 0)
				compare = -1;
			return compare;
		});
	}

	@Override
	default ObservableSortedSet<E> subSet(Comparable<? super E> from, Comparable<? super E> to) {
		return new ObservableSortedSetImpl.ObservableSubSet<>(this, from, to);
	}

	@Override
	default <T> UniqueSortedDataFlow<E, E, E> flow() {
		return new ObservableSortedSetImpl.UniqueSortedBaseFlow<>(this);
	}

	/**
	 * Turns an observable value containing an observable sorted set into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A sorted set representing the contents of the value, or a zero-length set when null
	 */
	public static <E> ObservableSortedSet<E> flattenValue(ObservableValue<? extends ObservableSortedSet<E>> collectionObservable) {
		return new ObservableSortedSetImpl.FlattenedValueSortedSet<>(collectionObservable);
	}
}
