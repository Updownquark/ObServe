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
import org.qommons.collect.BetterSortedSet;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A sorted set whose content can be observed.
 *
 * See <a href="https://github.com/Updownquark/ObServe/wiki/ObservableCollection-API#observablesortedset">the wiki</a> for more detail.
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSortedSet<E> extends ObservableSet<E>, BetterSortedSet<E> {
	/** This class's type key */
	@SuppressWarnings("rawtypes")
	static TypeTokens.TypeKey<ObservableSortedSet> TYPE_KEY = TypeTokens.get().keyFor(ObservableSortedSet.class)
	.enableCompoundTypes(new TypeTokens.UnaryCompoundTypeCreator<ObservableSortedSet>() {
		@Override
		public <P> TypeToken<? extends ObservableSortedSet> createCompoundType(TypeToken<P> param) {
			return new TypeToken<ObservableSortedSet<P>>() {}.where(new TypeParameter<P>() {}, param);
		}
	});
	/** This class's wildcard {@link TypeToken} */
	static TypeToken<ObservableSortedSet<?>> TYPE = TYPE_KEY.parameterized();

	@Override
	Equivalence.ComparatorEquivalence<? super E> equivalence();

	@Override
	default Iterator<E> iterator() {
		return ObservableSet.super.iterator();
	}

	@Override
	default Spliterator<E> spliterator() {
		return BetterSortedSet.super.spliterator();
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
	default E[] toArray() {
		return ObservableSet.super.toArray();
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

	/**
	 * Returns a value at or adjacent to another value
	 *
	 * @param search The search to find the target value
	 * @param filter The filter to direct and filter the search
	 * @param def Produces a default value in the case that no element of this set matches the given search
	 * @return An observable value with the result of the operation
	 */
	default ObservableElement<E> observeRelative(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter, Supplier<? extends E> def) {
		return new ObservableSortedSetImpl.RelativeFinder<>(this, search, filter);
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
		return (ObservableSortedSet<E>) BetterSortedSet.super.subSet(fromElement, fromInclusive, toElement, toInclusive);
	}

	@Override
	default ObservableSortedSet<E> subSet(Comparable<? super E> from, Comparable<? super E> to) {
		return new ObservableSortedSetImpl.ObservableSubSet<>(this, from, to);
	}

	@Override
	default ObservableSortedSet<E> headSet(E toElement, boolean inclusive) {
		return (ObservableSortedSet<E>) BetterSortedSet.super.headSet(toElement, inclusive);
	}

	@Override
	default ObservableSortedSet<E> tailSet(E fromElement, boolean inclusive) {
		return (ObservableSortedSet<E>) BetterSortedSet.super.tailSet(fromElement, inclusive);
	}

	@Override
	default ObservableSortedSet<E> subSet(E fromElement, E toElement) {
		return (ObservableSortedSet<E>) BetterSortedSet.super.subSet(fromElement, toElement);
	}

	@Override
	default ObservableSortedSet<E> headSet(E toElement) {
		return (ObservableSortedSet<E>) BetterSortedSet.super.headSet(toElement);
	}

	@Override
	default ObservableSortedSet<E> tailSet(E fromElement) {
		return (ObservableSortedSet<E>) BetterSortedSet.super.tailSet(fromElement);
	}

	@Override
	default ObservableSortedSet<E> with(E... values) {
		ObservableSet.super.with(values);
		return this;
	}

	@Override
	default ObservableSortedSet<E> withAll(Collection<? extends E> values) {
		ObservableSet.super.withAll(values);
		return this;
	}

	@Override
	default <T> DistinctSortedDataFlow<E, E, E> flow() {
		return new ObservableSortedSetImpl.DistinctSortedBaseFlow<>(this);
	}

	/**
	 * @param <E> The type for the set
	 * @param type The type for the set
	 * @param compare The comparator for the set
	 * @param values The values to be in the immutable set
	 * @return An immutable set with the given values
	 */
	static <E> ObservableSortedSet<E> of(TypeToken<E> type, Comparator<? super E> compare, E... values) {
		return of(type, compare, Arrays.asList(values));
	}

	/**
	 * @param <E> The type for the set
	 * @param type The type for the set
	 * @param compare The comparator for the set
	 * @param values The values to be in the immutable set
	 * @return An immutable set with the given values
	 */
	static <E> ObservableSortedSet<E> of(TypeToken<E> type, Comparator<? super E> compare, Collection<? extends E> values) {
		java.util.TreeSet<E> valueSet = new java.util.TreeSet<>(compare);
		valueSet.addAll(values);
		return ObservableCollection.create(type, new BetterTreeList<E>(false).withAll(valueSet))//
			.flow().distinctSorted(compare, false).unmodifiable(false).collect();
	}

	/**
	 * @param <E> The type for the set
	 * @param type The type for the set
	 * @param compare The comparator to use to sort the set's values
	 * @return A new, empty, mutable observable sorted set
	 */
	static <E> ObservableSortedSet<E> create(TypeToken<E> type, Comparator<? super E> compare) {
		return create(type, createDefaultBacking(compare));
	}

	/**
	 * @param <E> The type for the set
	 * @param type The type for the set
	 * @param compare The comparator to use to sort the set's values
	 * @return A builder to create a new, empty, mutable observable sorted set
	 */
	static <E> DefaultObservableSortedSet.Builder<E, ?> build(TypeToken<E> type, Comparator<? super E> compare) {
		return DefaultObservableSortedSet.build(type, compare);
	}

	/**
	 * @param <E> The type for the set
	 * @param type The type for the set
	 * @param compare The comparator to use to sort the set's values
	 * @return A builder to create a new, empty, mutable observable sorted set
	 */
	static <E> DefaultObservableSortedSet.Builder<E, ?> build(Class<E> type, Comparator<? super E> compare) {
		return build(TypeTokens.get().of(type), compare);
	}

	/**
	 * @param <E> The type for the set
	 * @param compare The comparator to use to sort the set's values
	 * @return A new sorted set to back a collection created by {@link #create(TypeToken, Comparator)}
	 */
	static <E> BetterSortedSet<E> createDefaultBacking(Comparator<? super E> compare) {
		return new BetterTreeSet<>(true, compare);
	}

	/**
	 * @param <E> The type for the set
	 * @param type The type for the set
	 * @param backing The sorted set to hold the observable set's data
	 * @return A new, empty, mutable observable sorted set whose performance and storage characteristics are determined by
	 *         <code>backing</code>
	 */
	static <E> ObservableSortedSet<E> create(TypeToken<E> type, BetterSortedSet<E> backing) {
		return new DefaultObservableSortedSet<>(type, backing);
	}

	/**
	 * Turns an observable value containing an observable sorted set into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @param compare The comparator for the set. The contents of the value must always have the same comparator.
	 * @return A sorted set representing the contents of the value, or a zero-length set when null
	 */
	public static <E> ObservableSortedSet<E> flattenValue(ObservableValue<? extends ObservableSortedSet<? extends E>> collectionObservable,
		Comparator<? super E> compare) {
		return new ObservableSortedSetImpl.FlattenedValueSortedSet<>(collectionObservable, compare);
	}
}
