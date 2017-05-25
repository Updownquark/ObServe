package org.observe.collect;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.qommons.Ternian;
import org.qommons.TriFunction;
import org.qommons.collect.Betterator;
import org.qommons.collect.ReversibleList;
import org.qommons.collect.TransactableList;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A {@link List} extension of {@link ObservableCollection}
 *
 * @param <E> The type of element in the list
 */
public interface ObservableList<E> extends ObservableReversibleCollection<E>, ReversibleList<E>, TransactableList<E> {
	@Override
	default Betterator<E> iterator() {
		return ReversibleList.super.iterator();
	}

	@Override
	abstract ObservableReversibleSpliterator<E> spliterator();

	@Override
	default void replaceAll(UnaryOperator<E> op) {
		ObservableReversibleCollection.super.replaceAll(op);
	}

	@Override
	default E[] toArray() {
		return ObservableReversibleCollection.super.toArray();
	}

	@Override
	default <T> T[] toArray(T[] a) {
		return ObservableReversibleCollection.super.toArray(a);
	}

	@Override
	abstract void removeRange(int fromIndex, int toIndex);

	@Override
	abstract ObservableReversibleSpliterator<E> spliterator(int index);

	@Override
	default ListIterator<E> listIterator() {
		return listIterator(0);
	}

	// @Override
	// default ListIterator<E> listIterator(int index) {
	// return new ObservableListImpl.SimpleListIterator<>(this, index);
	// }

	// /**
	// * A sub-list of this list. The returned list is backed by this list and updated along with it. The index arguments may be any
	// * non-negative value. If this list's size is {@code <=fromIndex}, the list will be empty. If {@code toIndex>} this list's size, the
	// * returned list's size may be less than {@code toIndex-fromIndex}.
	// *
	// * @see java.util.List#subList(int, int)
	// */
	// @Override
	// default ReversibleList<E> subList(int fromIndex, int toIndex) {
	// return new ObservableListImpl.SubListImpl<>(this, this, fromIndex, toIndex);
	// }

	@Override
	default ObservableList<E> reverse() {
		return new ObservableListImpl.ReversedList<>(this);
	}

	@Override
	default ObservableReversibleCollection<E> withEquivalence(Equivalence<? super E> otherEquiv) {
		return new ObservableListImpl.EquivalenceSwitchedObservableList<>(this, otherEquiv);
	}

	@Override
	default ObservableList<E> filter(Function<? super E, String> filter) {
		return (ObservableList<E>) ObservableReversibleCollection.super.filter(filter);
	}

	@Override
	default <T> ObservableList<T> filter(Class<T> type) {
		return (ObservableList<T>) ObservableReversibleCollection.super.filter(type);
	}

	@Override
	default <T> ObservableList<T> map(Function<? super E, T> map) {
		return (ObservableList<T>) ObservableReversibleCollection.super.map(map);
	}

	@Override
	default <T> MappedListBuilder<E, E, T> buildMap(TypeToken<T> type) {
		return new MappedListBuilder<>(this, null, type);
	}

	@Override
	default <T> ObservableList<T> filterMap(FilterMapDef<E, ?, T> filterMap) {
		return new ObservableListImpl.FilterMappedObservableList<>(this, filterMap);
	}

	@Override
	default <T> ObservableList<T> flatMapValues(TypeToken<T> type, Function<? super E, ? extends ObservableValue<? extends T>> map) {
		TypeToken<ObservableValue<? extends T>> collectionType;
		if (type == null) {
			collectionType = (TypeToken<ObservableValue<? extends T>>) TypeToken.of(map.getClass())
				.resolveType(Function.class.getTypeParameters()[1]);
			if (!collectionType.isAssignableFrom(new TypeToken<ObservableList<T>>() {}))
				collectionType = new TypeToken<ObservableValue<? extends T>>() {};
		} else {
			collectionType = new TypeToken<ObservableValue<? extends T>>() {}.where(new TypeParameter<T>() {}, type);
		}
		return flattenValues(this.<ObservableValue<? extends T>> buildMap(collectionType).map(map, false).build());
	}

	/**
	 * Shorthand for {@link #flatten(ObservableList) flatten}({@link #map(Function) map}(Function))
	 *
	 * @param <T> The type of the values produced
	 * @param type The type of the values produced
	 * @param map The value producer
	 * @return A list whose values are the accumulation of all those produced by applying the given function to all of this list's values
	 */
	default <T> ObservableList<T> flatMapList(TypeToken<T> type, Function<? super E, ? extends ObservableList<? extends T>> map) {
		TypeToken<ObservableList<? extends T>> collectionType;
		if (type == null) {
			collectionType = (TypeToken<ObservableList<? extends T>>) TypeToken.of(map.getClass())
				.resolveType(Function.class.getTypeParameters()[1]);
			if (!collectionType.isAssignableFrom(new TypeToken<ObservableList<T>>() {}))
				collectionType = new TypeToken<ObservableList<? extends T>>() {};
		} else {
			collectionType = new TypeToken<ObservableList<? extends T>>() {}.where(new TypeParameter<T>() {}, type);
		}
		return flatten(this.<ObservableList<? extends T>> buildMap(collectionType).map(map, false).build());
	}

	@Override
	default <T, V> CombinedListBuilder2<E, T, V> combineWith(ObservableValue<T> arg, TypeToken<V> targetType) {
		return new CombinedListBuilder2<>(this, arg, targetType);
	}

	@Override
	default <V> ObservableList<V> combine(CombinedCollectionDef<E, V> combination) {
		return new ObservableListImpl.CombinedObservableList<>(this, combination);
	}

	@Override
	default ListModFilterBuilder<E> filterModification() {
		return new ListModFilterBuilder<>(this);
	}

	@Override
	default ObservableList<E> filterModification(ModFilterDef<E> filter) {
		return new ObservableListImpl.ModFilteredObservableList<>(this, filter);
	}

	@Override
	default ObservableList<E> cached(Observable<?> until) {
		return new ObservableListImpl.CachedObservableList<>(this, until);
	}

	@Override
	default ObservableList<E> sorted(Comparator<? super E> compare) {
		return new SortedObservableList<>(this, compare);
	}

	@Override
	default ObservableList<E> refresh(Observable<?> refresh) {
		return new ObservableListImpl.RefreshingList<>(this, refresh);
	}

	@Override
	default ObservableList<E> refreshEach(Function<? super E, Observable<?>> refire) {
		return new ObservableListImpl.ElementRefreshingList<>(this, refire);
	}

	@Override
	default ObservableList<E> takeUntil(Observable<?> until) {
		return new ObservableListImpl.TakenUntilObservableList<>(this, until, true);
	}

	@Override
	default ObservableList<E> unsubscribeOn(Observable<?> until) {
		return new ObservableListImpl.TakenUntilObservableList<>(this, until, false);
	}

	/**
	 * @param <T> The type of the value to wrap
	 * @param type The type of the elements in the list
	 * @param list The list of items for the new list
	 * @return An observable list whose contents are given and never changes
	 */
	public static <T> ObservableList<T> constant(TypeToken<T> type, List<? extends T> list) {
		return new ObservableListImpl.ConstantObservableList<>(type, list);
	}

	/**
	 * @param <T> The type of the elements
	 * @param type The type of the elements in the list
	 * @param list The array of items for the new list
	 * @return An observable list whose contents are given and never changes
	 */
	public static <T> ObservableList<T> constant(TypeToken<T> type, T... list) {
		return constant(type, java.util.Arrays.asList(list));
	}

	/**
	 * Turns a list of observable values into a list composed of those holders' values
	 *
	 * @param <E> The type of elements held in the values
	 * @param list The list to flatten
	 * @return The flattened list
	 */
	public static <E> ObservableList<E> flattenValues(ObservableList<? extends ObservableValue<? extends E>> list) {
		return new ObservableListImpl.FlattenedObservableValuesList<>(list);
	}

	/**
	 * Turns an observable value containing an observable list into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A list representing the contents of the value, or a zero-length list when null
	 */
	public static <E> ObservableList<E> flattenValue(ObservableValue<ObservableList<E>> collectionObservable) {
		return new ObservableListImpl.FlattenedObservableValueList<>(collectionObservable);
	}

	/**
	 * Flattens a list of lists.
	 *
	 * @param <E> The super-type of all list in the wrapping list
	 * @param list The list to flatten
	 * @return A list containing all elements of all lists in the outer list
	 */
	public static <E> ObservableList<E> flatten(ObservableList<? extends ObservableList<? extends E>> list) {
		return new ObservableListImpl.FlattenedObservableList<>(list);
	}

	/**
	 * @param <T> The super type of elements in the lists
	 * @param type The super type of all possible lists in the outer list
	 * @param lists The lists to flatten
	 * @return An observable list that contains all the values of the given lists
	 */
	public static <T> ObservableList<T> flattenLists(TypeToken<T> type, ObservableList<? extends T>... lists) {
		type = type.wrap();
		if (lists.length == 0)
			return constant(type);
		ObservableList<ObservableList<T>> wrapper = constant(new TypeToken<ObservableList<T>>() {}.where(new TypeParameter<T>() {}, type),
			(ObservableList<T>[]) lists);
		return flatten(wrapper);
	}

	/**
	 * A {@link ObservableCollection.MappedCollectionBuilder} that builds an {@link ObservableList}
	 *
	 * @param <E> The type of values in the source list
	 * @param <I> Intermediate type
	 * @param <T> The type of values in the mapped list
	 */
	class MappedListBuilder<E, I, T> extends MappedReversibleCollectionBuilder<E, I, T> {
		protected MappedListBuilder(ObservableList<E> wrapped, MappedListBuilder<E, ?, I> parent, TypeToken<T> type) {
			super(wrapped, parent, type);
		}

		@Override
		protected ObservableList<E> getCollection() {
			return (ObservableList<E>) super.getCollection();
		}

		@Override
		public MappedListBuilder<E, I, T> filter(Function<? super I, String> filter, boolean filterNulls) {
			return (MappedListBuilder<E, I, T>) super.filter(filter, filterNulls);
		}

		@Override
		public MappedListBuilder<E, I, T> map(Function<? super I, ? extends T> map, boolean mapNulls) {
			return (MappedListBuilder<E, I, T>) super.map(map, mapNulls);
		}

		@Override
		public MappedListBuilder<E, I, T> withReverse(Function<? super T, ? extends I> reverse, boolean reverseNulls) {
			return (MappedListBuilder<E, I, T>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public ObservableList<T> build() {
			return (ObservableList<T>) super.build();
		}

		@Override
		public <X> MappedListBuilder<E, T, X> andThen(TypeToken<X> nextType) {
			if (getMap() == null && !getCollection().getType().equals(getType()))
				throw new IllegalStateException("Type-mapped collection builder with no map defined");
			return new MappedListBuilder<>(getCollection(), this, nextType);
		}
	}

	/**
	 * A {@link ObservableCollection.CombinedCollectionBuilder} that builds an {@link ObservableReversibleCollection}
	 *
	 * @param <E> The type of elements in the source list
	 * @param <V> The type of elements in the resulting list
	 * @see ObservableOrderedCollection#combineWith(ObservableValue, TypeToken)
	 * @see ObservableOrderedCollection.CombinedOrderedCollectionBuilder3#and(ObservableValue)
	 */
	interface CombinedListBuilder<E, V> extends CombinedReversibleCollectionBuilder<E, V> {
		@Override
		<T> CombinedListBuilder<E, V> and(ObservableValue<T> arg);

		@Override
		<T> CombinedListBuilder<E, V> and(ObservableValue<T> arg, boolean combineNulls);

		@Override
		CombinedListBuilder<E, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends E> reverse, boolean reverseNulls);

		@Override
		ObservableList<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination);

		@Override
		CombinedCollectionDef<E, V> toDef(Function<? super CombinedValues<? extends E>, ? extends V> combination);
	}

	/**
	 * A {@link ObservableList.CombinedListBuilder} for the combination of a list with a single value. Use {@link #and(ObservableValue)} to
	 * combine with additional values.
	 *
	 * @param <E> The type of elements in the source list
	 * @param <T> The type of the combined value
	 * @param <V> The type of elements in the resulting list
	 * @see ObservableList#combineWith(ObservableValue, TypeToken)
	 */
	class CombinedListBuilder2<E, T, V> extends CombinedReversibleCollectionBuilder2<E, T, V> implements CombinedListBuilder<E, V> {
		public CombinedListBuilder2(ObservableList<E> list, ObservableValue<T> arg2, TypeToken<V> targetType) {
			super(list, arg2, targetType);
		}

		@Override
		public ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
		}

		@Override
		public CombinedListBuilder2<E, T, V> combineNulls(boolean combineNulls) {
			return (CombinedListBuilder2<E, T, V>) super.combineNulls(combineNulls);
		}

		@Override
		public CombinedListBuilder2<E, T, V> combineCollectionNulls(boolean combineNulls) {
			return (CombinedListBuilder2<E, T, V>) super.combineCollectionNulls(combineNulls);
		}

		@Override
		public CombinedListBuilder2<E, T, V> combineNullArg2(boolean combineNulls) {
			return (CombinedListBuilder2<E, T, V>) super.combineNullArg2(combineNulls);
		}

		@Override
		public CombinedListBuilder2<E, T, V> withReverse(BiFunction<? super V, ? super T, ? extends E> reverse, boolean reverseNulls) {
			return (CombinedListBuilder2<E, T, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public CombinedListBuilder2<E, T, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends E> reverse,
			boolean reverseNulls) {
			return (CombinedListBuilder2<E, T, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public ObservableList<V> build(BiFunction<? super E, ? super T, ? extends V> combination) {
			return (ObservableList<V>) super.build(combination);
		}

		@Override
		public ObservableList<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return (ObservableList<V>) super.build(combination);
		}

		@Override
		public <U> CombinedListBuilder3<E, T, U, V> and(ObservableValue<U> arg3) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedListBuilder3<>(this, arg3, Ternian.NONE);
		}

		@Override
		public <U> CombinedListBuilder3<E, T, U, V> and(ObservableValue<U> arg3, boolean combineNulls) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedListBuilder3<>(this, arg3, Ternian.of(combineNulls));
		}
	}

	/**
	 * A {@link ObservableList.CombinedListBuilder} for the combination of a list with 2 values. Use {@link #and(ObservableValue)} to
	 * combine with additional values.
	 *
	 * @param <E> The type of elements in the source list
	 * @param <T> The type of the first combined value
	 * @param <U> The type of the second combined value
	 * @param <V> The type of elements in the resulting list
	 * @see ObservableList#combineWith(ObservableValue, TypeToken)
	 * @see ObservableList.CombinedListBuilder2#and(ObservableValue)
	 */
	class CombinedListBuilder3<E, T, U, V> extends CombinedReversibleCollectionBuilder3<E, T, U, V> implements CombinedListBuilder<E, V> {
		public CombinedListBuilder3(CombinedListBuilder2<E, T, V> combine2, ObservableValue<U> arg3, Ternian combineNulls) {
			super(combine2, arg3, combineNulls);
		}

		@Override
		public ObservableList<E> getSource() {
			return (ObservableList<E>) getCombine2().getSource();
		}

		@Override
		public CombinedListBuilder3<E, T, U, V> withReverse(TriFunction<? super V, ? super T, ? super U, ? extends E> reverse,
			boolean reverseNulls) {
			return (CombinedListBuilder3<E, T, U, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public CombinedListBuilder3<E, T, U, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends E> reverse,
			boolean reverseNulls) {
			return (CombinedListBuilder3<E, T, U, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public ObservableList<V> build(TriFunction<? super E, ? super T, ? super U, ? extends V> combination) {
			return (ObservableList<V>) super.build(combination);
		}

		@Override
		public ObservableList<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return (ObservableList<V>) super.build(combination);
		}

		@Override
		public <T2> CombinedListBuilderN<E, V> and(ObservableValue<T2> arg) {
			if (getCombine2().getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedListBuilderN<>(this).and(arg);
		}

		@Override
		public <T2> CombinedListBuilderN<E, V> and(ObservableValue<T2> arg, boolean combineNulls) {
			if (getCombine2().getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedListBuilderN<>(this).and(arg, combineNulls);
		}
	}

	/**
	 * A {@link ObservableReversibleCollection.CombinedReversibleCollectionBuilder} for the combination of a list with one or more
	 * (typically at least 3) values. Use {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source list
	 * @param <V> The type of elements in the resulting list
	 * @see ObservableReversibleCollection#combineWith(ObservableValue, TypeToken)
	 * @see ObservableReversibleCollection.CombinedReversibleCollectionBuilder3#and(ObservableValue)
	 */
	class CombinedListBuilderN<E, V> extends CombinedReversibleCollectionBuilderN<E, V> implements CombinedListBuilder<E, V> {
		public CombinedListBuilderN(CombinedReversibleCollectionBuilder3<E, ?, ?, V> combine3) {
			super(combine3);
		}

		@Override
		public CombinedListBuilderN<E, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends E> reverse,
			boolean reverseNulls) {
			return (CombinedListBuilderN<E, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public <T> CombinedListBuilderN<E, V> and(ObservableValue<T> arg) {
			return (CombinedListBuilderN<E, V>) super.and(arg);
		}

		@Override
		public <T> CombinedListBuilderN<E, V> and(ObservableValue<T> arg, boolean combineNull) {
			return (CombinedListBuilderN<E, V>) super.and(arg, combineNull);
		}

		@Override
		public ObservableList<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return (ObservableList<V>) super.build(combination);
		}

		@Override
		public CombinedCollectionDef<E, V> toDef(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return new CombinedCollectionDef<>(getTargetType(), addArgs(new LinkedHashMap<>(2)), combination, areCollectionNullsCombined(),
				getReverse(), areNullsReversed(), false);
		}
	}

	/**
	 * Builds a modification filter that may prevent certain kinds of modification to the list
	 *
	 * @param <E> The type of elements in the list
	 */
	class ListModFilterBuilder<E> extends ReversibleModFilterBuilder<E> {
		public ListModFilterBuilder(ObservableList<E> list) {
			super(list);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
		}

		@Override
		public ListModFilterBuilder<E> immutable(String modMsg) {
			return (ListModFilterBuilder<E>) super.immutable(modMsg);
		}

		@Override
		public ListModFilterBuilder<E> noAdd(String modMsg) {
			return (ListModFilterBuilder<E>) super.noAdd(modMsg);
		}

		@Override
		public ListModFilterBuilder<E> noRemove(String modMsg) {
			return (ListModFilterBuilder<E>) super.noRemove(modMsg);
		}

		@Override
		public ListModFilterBuilder<E> filterAdd(Function<? super E, String> messageFn) {
			return (ListModFilterBuilder<E>) super.filterAdd(messageFn);
		}

		@Override
		public ListModFilterBuilder<E> filterRemove(Function<? super E, String> messageFn) {
			return (ListModFilterBuilder<E>) super.filterRemove(messageFn);
		}

		@Override
		public ObservableList<E> build() {
			return (ObservableList<E>) super.build();
		}
	}
}
