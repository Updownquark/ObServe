package org.observe.collect;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
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
	default ObservableElementSpliterator<E> spliterator() {
		return ObservableSet.super.spliterator();
	}

	@Override
	default int indexOf(Object value) {
		return ObservableSet.super.indexOf(value);
	}

	@Override
	default int lastIndexOf(Object value) {
		return ObservableSet.super.lastIndexOf(value);
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
	default boolean forValue(Comparable<? super E> search, boolean up, Consumer<? super E> onValue) {
		return forObservableElement(search, up, el -> onValue.accept(el.get()));
	}

	@Override
	default boolean forElement(Comparable<? super E> search, boolean up, Consumer<? super CollectionElement<? extends E>> onElement) {
		return forMutableElement(search, up, onElement);
	}

	@Override
	default boolean forElement(E value, Consumer<? super CollectionElement<? extends E>> onElement, boolean first) {
		return ObservableSet.super.forElement(value, onElement, first);
	}

	boolean forObservableElement(Comparable<? super E> search, boolean up, Consumer<? super ObservableCollectionElement<? extends E>> onElement);

	boolean forMutableElement(Comparable<? super E> search, boolean up, Consumer<? super MutableObservableElement<? extends E>> onElement);

	@Override
	default boolean forObservableElement(E value, Consumer<? super ObservableCollectionElement<? extends E>> onElement, boolean first) {
		boolean[] found = new boolean[1];
		forObservableElement(v -> comparator().compare(value, v), first, el -> {
			if (equivalence().elementEquals(el.get(), value)) {
				found[0] = true;
				onElement.accept(el);
			}
		});
		return found[0];
	}

	@Override
	default boolean forMutableElement(E value, Consumer<? super MutableObservableElement<? extends E>> onElement, boolean first) {
		boolean[] found = new boolean[1];
		forMutableElement(v -> comparator().compare(value, v), first, el -> {
			if (equivalence().elementEquals(el.get(), value)) {
				found[0] = true;
				onElement.accept(el);
			}
		});
		return found[0];
	}

	@Override
	default E first() {
		return getFirst();
	}

	@Override
	default E last() {
		return getLast();
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
	default E floor(E e) {
		return observeRelative(v -> comparator().compare(e, v), false).get();
	}

	@Override
	default E lower(E e) {
		return observeRelative(v -> {
			int compare = comparator().compare(e, v);
			if (compare == 0)
				return 1;
			return compare;
		}, false).get();
	}

	@Override
	default E ceiling(E e) {
		return observeRelative(v -> comparator().compare(e, v), true).get();
	}

	@Override
	default E higher(E e) {
		return observeRelative(v -> {
			int compare = comparator().compare(e, v);
			if (compare == 0)
				compare = -1;
			return compare;
		}, true).get();
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

	@Override
	default ObservableElementSpliterator<E> spliterator(Comparable<? super E> search, boolean up) {
		return mutableSpliterator(search, up).immutable();
	}

	@Override
	MutableObservableSpliterator<E> mutableSpliterator(Comparable<? super E> search, boolean up);

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
	default ObservableSortedSet<E> headSet(E toElement, boolean inclusive) {
		return subSet(null, v -> {
			int compare = comparator().compare(toElement, v);
			if (!inclusive && compare == 0)
				compare = -1;
			return compare;
		});
	}

	@Override
	default ObservableSortedSet<E> tailSet(E fromElement, boolean inclusive) {
		return subSet(v -> {
			int compare = comparator().compare(fromElement, v);
			if (!inclusive && compare == 0)
				compare = 1;
			return compare;
		}, null);
	}

	@Override
	default ObservableSortedSet<E> subSet(E fromElement, E toElement) {
		return subSet(fromElement, true, toElement, false);
	}

	@Override
	default ObservableSortedSet<E> headSet(E toElement) {
		return headSet(toElement, false);
	}

	@Override
	default ObservableSortedSet<E> tailSet(E fromElement) {
		return tailSet(fromElement, true);
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
