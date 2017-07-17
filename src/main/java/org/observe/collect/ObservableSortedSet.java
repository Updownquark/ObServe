package org.observe.collect;

import java.util.Iterator;
import java.util.function.Consumer;

import org.observe.ObservableValue;
import org.qommons.collect.ImmutableIterator;
import org.qommons.collect.TransactableSortedSet;

/**
 * A sorted set whose content can be observed
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSortedSet<E> extends ObservableSet<E>, TransactableSortedSet<E> {
	@Override
	default ImmutableIterator<E> iterator() {
		return ObservableSet.super.iterator();
	}

	@Override
	default ObservableElementSpliterator<E> spliterator() {
		return ObservableSet.super.spliterator();
	}

	/**
	 * Returns a value at or adjacent to another value
	 *
	 * @param value The relative value
	 * @param up Whether to get the closest value greater or less than the given value
	 * @param withValue Whether to return the given value if it exists in the map
	 * @return An observable value with the result of the operation
	 */
	ObservableValue<E> relative(E value, boolean up, boolean withValue);

	boolean forElement(E value, boolean up, boolean withValue, Consumer<? super ObservableCollectionElement<? extends E>> onElement);

	boolean forMutableElement(E value, boolean up, boolean withValue, Consumer<? super MutableObservableElement<? extends E>> onElement);

	@Override
	default boolean forObservableElement(E value, Consumer<? super ObservableCollectionElement<? extends E>> onElement, boolean first) {
		boolean[] found = new boolean[1];
		forElement(value, first, true, el -> {
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
		forMutableElement(value, first, true, el -> {
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
		return relative(e, false, true).get();
	}

	@Override
	default E lower(E e) {
		return relative(e, false, false).get();
	}

	@Override
	default E ceiling(E e) {
		return relative(e, true, true).get();
	}

	@Override
	default E higher(E e) {
		return relative(e, true, false).get();
	}

	@Override
	default ObservableSortedSet<E> reverse() {
		return descendingSet();
	}

	@Override
	default ObservableSortedSet<E> descendingSet() {
		return reverse();
	}

	@Override
	default Iterator<E> descendingIterator() {
		return reverse().iterator();
	}

	default ObservableElementSpliterator<E> spliterator(E value, boolean up, boolean withValue) {
		return mutableSpliterator(value, up, withValue).immutable();
	}

	MutableObservableSpliterator<E> mutableSpliterator(E value, boolean up, boolean withValue);

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
		return new ObservableSortedSetImpl.ObservableSubSet<>(this, fromElement, fromInclusive, toElement, toInclusive);
	}

	@Override
	default ObservableSortedSet<E> headSet(E toElement, boolean inclusive) {
		return subSet(null, true, toElement, inclusive);
	}

	@Override
	default ObservableSortedSet<E> tailSet(E fromElement, boolean inclusive) {
		return subSet(fromElement, inclusive, null, true);
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
