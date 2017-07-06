package org.observe.collect;

import java.util.List;
import java.util.ListIterator;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.ObservableCollectionImpl.AbstractDataFlow;
import org.qommons.Ternian;
import org.qommons.TriFunction;
import org.qommons.collect.ReversibleList;
import org.qommons.collect.TransactableList;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A {@link List} extension of {@link ObservableCollection}
 *
 * @param <E> The type of element in the list
 */
public interface ObservableList<E> extends ObservableCollection<E>, ReversibleList<E>, TransactableList<E> {
	@Override
	default ObservableElementSpliterator<E> spliterator() {
		return ObservableCollection.super.spliterator();
	}

	@Override
	default int indexOf(Object o) {
		return ObservableCollection.super.indexOf(o);
	}

	@Override
	default int lastIndexOf(Object o) {
		return ObservableCollection.super.lastIndexOf(o);
	}

	@Override
	default E[] toArray() {
		return ObservableCollection.super.toArray();
	}

	@Override
	default <T> T[] toArray(T[] a) {
		return ObservableCollection.super.toArray(a);
	}

	@Override
	default void replaceAll(UnaryOperator<E> op) {
		ObservableCollection.super.replaceAll(op);
	}

	@Override
	abstract void removeRange(int fromIndex, int toIndex);

	@Override
	abstract ObservableElementSpliterator<E> spliterator(int index);

	@Override
	abstract MutableObservableSpliterator<E> mutableSpliterator(int index);

	@Override
	default ListIterator<E> listIterator() {
		return listIterator(0);
	}

	@Override
	default ObservableList<E> reverse() {
		return new ObservableListImpl.ReversedList<>(this);
	}

	@Override
	default ListDataFlow<E, E, E> flow() {
		return new ObservableListImpl.BaseListDataFlow<>(this);
	}

	@Override
	default ListViewBuilder<E> view() {
		return new ListViewBuilder<>(this);
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

	interface ListDataFlow<E, I, T> extends CollectionDataFlow<E, I, T> {
		@Override
		ListDataFlow<E, T, T> withEquivalence(Equivalence<? super T> equivalence);

		@Override
		ListDataFlow<E, T, T> refresh(Observable<?> refresh);

		@Override
		ListDataFlow<E, T, T> refreshEach(Function<? super T, ? extends Observable<?>> refresh);

		@Override
		<X> MappedListBuilder<E, T, X> map(TypeToken<X> target);

		@Override
		default <X> ListDataFlow<E, ?, X> flatMap(TypeToken<X> target, Function<? super T, ? extends ObservableValue<? extends X>> map) {
			return (ListDataFlow<E, ?, X>) CollectionDataFlow.super.flatMap(target, map);
		}

		@Override
		<V, X> CombinedListBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, TypeToken<X> target);

		@Override
		<V, X> CombinedListBuilder2<E, T, V, X> combineWith(ObservableValue<V> value, boolean combineNulls, TypeToken<X> target);

		@Override
		ObservableList<T> build();
	}

	/**
	 * A {@link ObservableCollection.MappedCollectionBuilder} that builds an {@link ObservableList}
	 *
	 * @param <E> The type of values in the source list
	 * @param <I> Intermediate type
	 * @param <T> The type of values in the mapped list
	 */
	class MappedListBuilder<E, I, T> extends MappedCollectionBuilder<E, I, T> {
		protected MappedListBuilder(AbstractDataFlow<E, ?, I> parent, TypeToken<T> type) {
			super(parent, type);
		}

		@Override
		public MappedListBuilder<E, I, T> withReverse(Function<? super T, ? extends I> reverse, boolean reverseNulls) {
			return (MappedListBuilder<E, I, T>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public MappedListBuilder<E, I, T> withElementSetting(ElementSetter<? super I, ? super T> reverse, boolean reverseNulls) {
			return (MappedListBuilder<E, I, T>) super.withElementSetting(reverse, reverseNulls);
		}

		@Override
		public ListDataFlow<E, I, T> map(Function<? super I, ? extends T> map, boolean mapNulls) {
			return new ObservableListImpl.MapListOp<>(getParent(), getTargetType(), map, mapNulls, getReverse(), getElementReverse(),
				areNullsReversed());
		}
	}

	/**
	 * A {@link ObservableCollection.CombinedCollectionBuilder} that builds an {@link ObservableReversibleCollection}
	 *
	 * @param <E> The type of elements in the source list
	 * @param <I> An intermediate type
	 * @param <V> The type of elements in the resulting list
	 */
	interface CombinedListBuilder<E, I, V> extends CombinedCollectionBuilder<E, I, V> {
		@Override
		<T> CombinedListBuilder<E, I, V> and(ObservableValue<T> arg);

		@Override
		<T> CombinedListBuilder<E, I, V> and(ObservableValue<T> arg, boolean combineNulls);

		@Override
		CombinedListBuilder<E, I, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends I> reverse, boolean reverseNulls);

		@Override
		ListDataFlow<E, I, V> build(Function<? super CombinedValues<? extends I>, ? extends V> combination);
	}

	/**
	 * A {@link ObservableList.CombinedListBuilder} for the combination of a list with a single value. Use {@link #and(ObservableValue)} to
	 * combine with additional values.
	 *
	 * @param <E> The type of elements in the source list
	 * @param <I> An intermediate type
	 * @param <T> The type of the combined value
	 * @param <V> The type of elements in the resulting list
	 */
	class CombinedListBuilder2<E, I, T, V> extends CombinedCollectionBuilder2<E, I, T, V> implements CombinedListBuilder<E, I, V> {
		protected CombinedListBuilder2(AbstractDataFlow<E, ?, I> parent, TypeToken<V> targetType, ObservableValue<T> arg2,
			Ternian combineNull) {
			super(parent, targetType, arg2, combineNull);
		}

		@Override
		public CombinedListBuilder2<E, I, T, V> combineNullsByDefault() {
			return (CombinedListBuilder2<E, I, T, V>) super.combineNullsByDefault();
		}

		@Override
		public CombinedListBuilder2<E, I, T, V> withReverse(BiFunction<? super V, ? super T, ? extends I> reverse, boolean reverseNulls) {
			return (CombinedListBuilder2<E, I, T, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public CombinedListBuilder2<E, I, T, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends I> reverse,
			boolean reverseNulls) {
			return (CombinedListBuilder2<E, I, T, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public ListDataFlow<E, I, V> build(BiFunction<? super I, ? super T, ? extends V> combination) {
			return (ListDataFlow<E, I, V>) super.build(combination);
		}

		@Override
		public ListDataFlow<E, I, V> build(Function<? super CombinedValues<? extends I>, ? extends V> combination) {
			return (ListDataFlow<E, I, V>) super.build(combination);
		}

		@Override
		public <U> CombinedListBuilder3<E, I, T, U, V> and(ObservableValue<U> arg3) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedListBuilder3<>(getParent(), getTargetType(), getArg2(), combineNulls(getArg2()), arg3, Ternian.NONE);
		}

		@Override
		public <U> CombinedListBuilder3<E, I, T, U, V> and(ObservableValue<U> arg3, boolean combineNulls) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedListBuilder3<>(getParent(), getTargetType(), getArg2(), combineNulls(getArg2()), arg3,
				Ternian.of(combineNulls));
		}
	}

	/**
	 * A {@link ObservableList.CombinedListBuilder} for the combination of a list with 2 values. Use {@link #and(ObservableValue)} to
	 * combine with additional values.
	 *
	 * @param <E> The type of elements in the source list
	 * @param <I> An intermediate type
	 * @param <T> The type of the first combined value
	 * @param <U> The type of the second combined value
	 * @param <V> The type of elements in the resulting list
	 * @see ObservableList.ListDataFlow#combineWith(ObservableValue, TypeToken)
	 * @see ObservableList.CombinedListBuilder2#and(ObservableValue)
	 */
	class CombinedListBuilder3<E, I, T, U, V> extends CombinedCollectionBuilder3<E, I, T, U, V> implements CombinedListBuilder<E, I, V> {
		protected CombinedListBuilder3(AbstractDataFlow<E, ?, I> parent, TypeToken<V> targetType, ObservableValue<T> arg2,
			Ternian combineArg2Nulls, ObservableValue<U> arg3, Ternian combineArg3Nulls) {
			super(parent, targetType, arg2, combineArg2Nulls, arg3, combineArg3Nulls);
		}

		@Override
		public CombinedListBuilder3<E, I, T, U, V> withReverse(TriFunction<? super V, ? super T, ? super U, ? extends I> reverse,
			boolean reverseNulls) {
			return (CombinedListBuilder3<E, I, T, U, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public CombinedListBuilder3<E, I, T, U, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends I> reverse,
			boolean reverseNulls) {
			return (CombinedListBuilder3<E, I, T, U, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public ListDataFlow<E, I, V> build(TriFunction<? super I, ? super T, ? super U, ? extends V> combination) {
			return (ListDataFlow<E, I, V>) super.build(combination);
		}

		@Override
		public ListDataFlow<E, I, V> build(Function<? super CombinedValues<? extends I>, ? extends V> combination) {
			return (ListDataFlow<E, I, V>) super.build(combination);
		}

		@Override
		public <T2> CombinedListBuilderN<E, I, V> and(ObservableValue<T2> arg) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedListBuilderN<>(getParent(), getTargetType(), getArg2(), combineNulls(getArg2()), getArg3(),
				combineNulls(getArg3()), arg, Ternian.NONE);
		}

		@Override
		public <T2> CombinedListBuilderN<E, I, V> and(ObservableValue<T2> arg, boolean combineNulls) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedListBuilderN<>(getParent(), getTargetType(), getArg2(), combineNulls(getArg2()), getArg3(),
				combineNulls(getArg3()), arg, Ternian.of(combineNulls));
		}
	}

	/**
	 * A {@link ObservableReversibleCollection.CombinedReversibleCollectionBuilder} for the combination of a list with one or more
	 * (typically at least 3) values. Use {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source list
	 * @param <I> An intermediate type
	 * @param <V> The type of elements in the resulting list
	 * @see ObservableReversibleCollection#combineWith(ObservableValue, TypeToken)
	 * @see ObservableReversibleCollection.CombinedReversibleCollectionBuilder3#and(ObservableValue)
	 */
	class CombinedListBuilderN<E, I, V> extends CombinedCollectionBuilderN<E, I, V> implements CombinedListBuilder<E, I, V> {
		protected CombinedListBuilderN(AbstractDataFlow<E, ?, I> parent, TypeToken<V> targetType, ObservableValue<?> arg2,
			Ternian combineArg2Nulls, ObservableValue<?> arg3, Ternian combineArg3Nulls, ObservableValue<?> arg4,
			Ternian combineArg4Nulls) {
			super(parent, targetType, arg2, combineArg2Nulls, arg3, combineArg3Nulls, arg4, combineArg4Nulls);
		}

		@Override
		public CombinedListBuilderN<E, I, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends I> reverse,
			boolean reverseNulls) {
			return (CombinedListBuilderN<E, I, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public <T> CombinedListBuilderN<E, I, V> and(ObservableValue<T> arg) {
			return (CombinedListBuilderN<E, I, V>) super.and(arg);
		}

		@Override
		public <T> CombinedListBuilderN<E, I, V> and(ObservableValue<T> arg, boolean combineNull) {
			return (CombinedListBuilderN<E, I, V>) super.and(arg, combineNull);
		}

		@Override
		public ListDataFlow<E, I, V> build(Function<? super CombinedValues<? extends I>, ? extends V> combination) {
			return (ListDataFlow<E, I, V>) super.build(combination);
		}
	}

	class ListViewBuilder<E> extends ViewBuilder<E> {
		public ListViewBuilder(ObservableList<E> collection) {
			super(collection);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
		}

		@Override
		public ObservableList<E> build() {
			return new ObservableListImpl.ListView<>(getSource(), toDef());
		}
	}
}
