package org.observe.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Supplier;

import org.observe.Equivalence;
import org.observe.ObservableValue;
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterSortedList;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.SortedTreeList;

import com.google.common.reflect.TypeToken;

/**
 * A sorted collection whose content can be observed.
 *
 * See <a href="https://github.com/Updownquark/ObServe/wiki/ObservableCollection-API#observablesortedcollection">the wiki</a> for more
 * detail.
 *
 * @param <E> The type of element in the collection
 */
public interface ObservableSortedCollection<E> extends ObservableCollection<E>, BetterSortedList<E> {
	/** This class's wildcard {@link TypeToken} */
	static TypeToken<ObservableSortedCollection<?>> TYPE = TypeTokens.get().keyFor(ObservableSortedCollection.class).wildCard();

	@Override
	Equivalence.ComparatorEquivalence<? super E> equivalence();

	@Override
	default Iterator<E> iterator() {
		return BetterSortedList.super.iterator();
	}

	@Override
	default Spliterator<E> spliterator() {
		return BetterSortedList.super.spliterator();
	}

	@Override
	default int indexOf(Object value) {
		return BetterSortedList.super.indexOf(value);
	}

	@Override
	default int lastIndexOf(Object value) {
		return BetterSortedList.super.lastIndexOf(value);
	}

	@Override
	default boolean contains(Object o) {
		return BetterSortedList.super.contains(o);
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		return ObservableCollection.super.containsAll(c);
	}

	@Override
	default E[] toArray() {
		return ObservableCollection.super.toArray();
	}

	@Override
	default boolean remove(Object c) {
		return BetterSortedList.super.remove(c);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return BetterSortedList.super.removeAll(c);
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return BetterSortedList.super.retainAll(c);
	}

	@Override
	abstract void clear();

	@Override
	default E lower(E e) {
		return BetterSortedList.super.lower(e);
	}

	@Override
	default E floor(E e) {
		return BetterSortedList.super.floor(e);
	}

	@Override
	default E ceiling(E e) {
		return BetterSortedList.super.ceiling(e);
	}

	@Override
	default E higher(E e) {
		return BetterSortedList.super.higher(e);
	}

	@Override
	default E first() {
		return BetterSortedList.super.first();
	}

	@Override
	default E last() {
		return BetterSortedList.super.last();
	}

	@Override
	default ObservableSortedCollection<E> subSequence(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return (ObservableSortedCollection<E>) BetterSortedList.super.subSequence(fromElement, fromInclusive, toElement, toInclusive);
	}

	@Override
	default ObservableSortedCollection<E> subSequence(Comparable<? super E> from, Comparable<? super E> to) {
		return new ObservableSortedCollectionImpl.ObservableSubSequence<>(this, from, to);
	}

	@Override
	default ObservableSortedCollection<E> headSequence(E toElement, boolean inclusive) {
		return (ObservableSortedCollection<E>) BetterSortedList.super.headSequence(toElement, inclusive);
	}

	@Override
	default ObservableSortedCollection<E> tailSequence(E fromElement, boolean inclusive) {
		return (ObservableSortedCollection<E>) BetterSortedList.super.tailSequence(fromElement, inclusive);
	}

	@Override
	default ObservableSortedCollection<E> subSequence(E fromElement, E toElement) {
		return (ObservableSortedCollection<E>) BetterSortedList.super.subSequence(fromElement, toElement);
	}

	@Override
	default ObservableSortedCollection<E> headSequence(E toElement) {
		return (ObservableSortedCollection<E>) BetterSortedList.super.headSequence(toElement);
	}

	@Override
	default ObservableSortedCollection<E> tailSequence(E fromElement) {
		return (ObservableSortedCollection<E>) BetterSortedList.super.tailSequence(fromElement);
	}

	/**
	 * Returns a value at or adjacent to another value
	 *
	 * @param search The search to find the target value
	 * @param filter The filter to direct and filter the search
	 * @param def Produces a default value in the case that no element of this collection matches the given search
	 * @return An observable value with the result of the operation
	 */
	default ObservableElement<E> observeRelative(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter,
		Supplier<? extends E> def) {
		return new ObservableSortedCollectionImpl.RelativeFinder<>(this, search, filter);
	}

	@Override
	default ObservableSortedCollection<E> reverse() {
		return new ObservableSortedCollectionImpl.ReversedSortedCollection<>(this);
	}

	@Override
	default ObservableSortedCollection<E> with(E... values) {
		ObservableCollection.super.with(values);
		return this;
	}

	@Override
	default ObservableSortedCollection<E> withAll(Collection<? extends E> values) {
		ObservableCollection.super.withAll(values);
		return this;
	}

	@Override
	default <T> SortedDataFlow<E, E, E> flow() {
		return new ObservableSortedCollectionImpl.SortedBaseFlow<>(this);
	}

	/**
	 * @param <E> The type for the collection
	 * @param type The type for the collection
	 * @param compare The comparator for the collection
	 * @param values The values to be in the immutable collection
	 * @return An immutable collection with the given values
	 */
	static <E> ObservableSortedCollection<E> of(TypeToken<E> type, Comparator<? super E> compare, E... values) {
		return of(type, compare, Arrays.asList(values));
	}

	/**
	 * @param <E> The type for the collection
	 * @param type The type for the collection
	 * @param compare The comparator for the collection
	 * @param values The values to be in the immutable collection
	 * @return An immutable collection with the given values
	 */
	static <E> ObservableSortedCollection<E> of(TypeToken<E> type, Comparator<? super E> compare, Collection<? extends E> values) {
		return ObservableCollection.create(type, new BetterTreeList<E>(false).withAll(values))//
			.flow().distinctSorted(compare, false).unmodifiable(false).collect();
	}

	/**
	 * @param <E> The type for the collection
	 * @param type The type for the collection
	 * @param compare The comparator to use to sort the collection's values
	 * @return A new, empty, mutable observable sorted collection
	 */
	static <E> ObservableSortedCollection<E> create(TypeToken<E> type, Comparator<? super E> compare) {
		return create(type, createDefaultBacking(compare));
	}

	/**
	 * @param <E> The type for the collection
	 * @param type The type for the collection
	 * @param compare The comparator to use to sort the collection's values
	 * @return A builder to create a new, empty, mutable observable sorted collection
	 */
	static <E> ObservableCollectionBuilder.SortedBuilder<E, ?> build(TypeToken<E> type, Comparator<? super E> compare) {
		return DefaultObservableSortedCollection.build(type, compare);
	}

	/**
	 * @param <E> The type for the collection
	 * @param type The type for the collection
	 * @param compare The comparator to use to sort the collection's values
	 * @return A builder to create a new, empty, mutable observable sorted collection
	 */
	static <E> ObservableCollectionBuilder.SortedBuilder<E, ?> build(Class<E> type, Comparator<? super E> compare) {
		return build(TypeTokens.get().of(type), compare);
	}

	/**
	 * @param <E> The type for the collection
	 * @param compare The comparator to use to sort the collection's values
	 * @return A new sorted collection to back a collection created by {@link #create(TypeToken, Comparator)}
	 */
	static <E> BetterSortedList<E> createDefaultBacking(Comparator<? super E> compare) {
		return new SortedTreeList<>(true, compare);
	}

	/**
	 * @param <E> The type for the collection
	 * @param type The type for the collection
	 * @param backing The sorted collection to hold the observable collection's data
	 * @return A new, empty, mutable observable sorted collection whose performance and storage characteristics are determined by
	 *         <code>backing</code>
	 */
	static <E> ObservableSortedCollection<E> create(TypeToken<E> type, BetterSortedList<E> backing) {
		return new DefaultObservableSortedCollection<>(type, backing);
	}

	/**
	 * Turns an observable value containing an observable sorted collection into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @param compare The comparator for the collection. The contents of the value must always have the same comparator.
	 * @return A sorted collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> ObservableSortedCollection<E> flattenValue(
		ObservableValue<? extends ObservableSortedCollection<? extends E>> collectionObservable, Comparator<? super E> compare) {
		return new ObservableSortedCollectionImpl.FlattenedValueSortedCollection<>(collectionObservable, compare);
	}
}
