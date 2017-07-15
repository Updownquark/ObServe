package org.observe.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.ObservableCollectionDataFlowImpl.AbstractDataFlow;
import org.qommons.Ternian;
import org.qommons.Transactable;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.collect.ReversibleElementSpliterator;
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
	default boolean contains(Object o) {
		return ObservableCollection.super.contains(o);
	}

	@Override
	default boolean containsAll(Collection<?> c) {
		return ObservableCollection.super.containsAll(c);
	}

	@Override
	default void replaceAll(UnaryOperator<E> op) {
		ObservableCollection.super.replaceAll(op);
	}

	@Override
	default boolean addAll(Collection<? extends E> c) {
		return ObservableCollection.super.addAll(c);
	}

	@Override
	default boolean remove(Object o) {
		return ObservableCollection.super.remove(o);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return ObservableCollection.super.removeAll(c);
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return ObservableCollection.super.removeAll(c);
	}

	@Override
	default void removeRange(int fromIndex, int toIndex) {
		try (Transaction t = lock(true, null)) {
			MutableObservableSpliterator<E> spliter = mutableSpliterator(fromIndex);
			for (int i = fromIndex; i < toIndex; i++)
				spliter.tryAdvanceElement(el -> el.remove(null));
		}
	}

	default void forElementAt(int index, Consumer<? super ObservableCollectionElement<? extends E>> onElement) {
		ofElementAt(index, el -> {
			onElement.accept(el);
			return null;
		});
	}

	default void forMutableElementAt(int index, Consumer<? super MutableObservableElement<? extends E>> onElement) {
		ofMutableElementAt(index, el -> {
			onElement.accept(el);
			return null;
		});
	}

	@Override
	<T> T ofElementAt(int index, Function<? super ObservableCollectionElement<? extends E>, T> onElement);

	@Override
	<T> T ofMutableElementAt(int index, Function<? super MutableObservableElement<? extends E>, T> onElement);

	@Override
	default E get(int index) {
		return ofElementAt(index, el -> el.get());
	}

	@Override
	default ObservableElementSpliterator<E> spliterator(int index) {
		return ObservableCollection.super.spliterator(index);
	}

	@Override
	default boolean addAll(int index, Collection<? extends E> c) {
		if (c.isEmpty())
			return false;
		forMutableElementAt(index, el -> {
			try (Transaction t = Transactable.lock(c, false, null)) {
				Spliterator<? extends E> spliter;
				if (c instanceof ReversedCollection)
					spliter = ((ReversedCollection<? extends E>) c).spliterator(false).reverse();
				else {
					ArrayList<E> list = new ArrayList<>(c);
					Collections.reverse(list);
					spliter = list.spliterator();
				}
				spliter.forEachRemaining(v -> ((MutableObservableElement<E>) el).add(v, true, null));
			}
		});
		return true;
	}

	@Override
	default E set(int index, E element) {
		return ofMutableElementAt(index, el -> ((MutableObservableElement<E>) el).set(element, null));
	}

	@Override
	default void add(int index, E element) {
		forMutableElementAt(index, el -> ((MutableObservableElement<E>) el).add(element, true, null));
	}

	@Override
	default E remove(int index) {
		return ofMutableElementAt(index, el -> {
			E old = el.get();
			el.remove(null);
			return old;
		});
	}

	@Override
	abstract MutableObservableSpliterator<E> mutableSpliterator(int index);

	@Override
	default ListIterator<E> listIterator() {
		return listIterator(0);
	}

	@Override
	default ListIterator<E> listIterator(int index) {
		return new ObservableListIterator<>(mutableSpliterator(index));
	}

	@Override
	default ReversibleList<E> subList(int fromIndex, int toIndex) {
		return new ObservableListImpl.SubList<>(this, fromIndex, toIndex);
	}

	@Override
	default ObservableList<E> reverse() {
		return new ObservableListImpl.ReversedList<>(this);
	}

	@Override
	default ListDataFlow<E, E, E> flow() {
		return new ObservableListImpl.BaseListDataFlow<>(this);
	}

	public static <E> ListDataFlow<E, E, E> create(TypeToken<E> type, Collection<? extends E> initialValues) {
		return ObservableCollectionImpl.createList(type, initialValues);
	}

	/**
	 * @param <E> The type of the value to wrap
	 * @param type The type of the elements in the list
	 * @param list The list of items for the new list
	 * @return An observable list whose contents are given and never changes
	 */
	public static <E> ListDataFlow<E, E, E> constant(TypeToken<E> type, List<? extends E> list) {
		return create(type, list).filterModification().immutable(StdMsg.UNSUPPORTED_OPERATION, false).build();
	}

	/**
	 * @param <E> The type of the elements
	 * @param type The type of the elements in the list
	 * @param list The array of items for the new list
	 * @return An observable list whose contents are given and never changes
	 */
	public static <E> ListDataFlow<E, E, E> constant(TypeToken<E> type, E... list) {
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
			return constant(type).collect();
		ObservableList<ObservableList<T>> wrapper = constant(new TypeToken<ObservableList<T>>() {}.where(new TypeParameter<T>() {}, type),
			(ObservableList<T>[]) lists).collect();
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
		ListModFilterBuilder<E, T> filterModification();

		@Override
		default ObservableList<T> collect() {
			return (ObservableList<T>) CollectionDataFlow.super.collect();
		}

		@Override
		ObservableList<T> collect(Observable<?> until);
	}

	/**
	 * A {@link ObservableCollection.MappedCollectionBuilder} that builds an {@link ObservableList}
	 *
	 * @param <E> The type of values in the source list
	 * @param <I> Intermediate type
	 * @param <T> The type of values in the mapped list
	 */
	class MappedListBuilder<E, I, T> extends MappedCollectionBuilder<E, I, T> {
		protected MappedListBuilder(ObservableList<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<T> type) {
			super(source, parent, type);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
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
			return new ObservableListImpl.MapListOp<>(getSource(), getParent(), getTargetType(), map, mapNulls, getReverse(),
				getElementReverse(), areNullsReversed(), isReEvalOnUpdate(), isFireIfUnchanged(), isCached());
		}
	}

	/**
	 * A {@link ObservableCollection.CombinedCollectionBuilder} that builds an {@link ObservableList}
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
		default ListDataFlow<E, I, V> build(Function<? super CombinedValues<? extends I>, ? extends V> combination) {
			return (ListDataFlow<E, I, V>) CombinedCollectionBuilder.super.build(combination);
		}

		@Override
		ListDataFlow<E, I, V> build(Function<? super CombinedValues<? extends I>, ? extends V> combination, boolean combineNulls);
	}

	/**
	 * A {@link ObservableList.CombinedListBuilder} for the combination of a list with a single value. Use {@link #and(ObservableValue)} to
	 * combine with additional values.
	 *
	 * @param <E> The type of elements in the source list
	 * @param <I> An intermediate type
	 * @param <T> The type of the combined value
	 * @param <V> The type of elements in the resulting list
	 * @see ObservableList.ListDataFlow#combineWith(ObservableValue, TypeToken)
	 */
	class CombinedListBuilder2<E, I, T, V> extends CombinedCollectionBuilder2<E, I, T, V> implements CombinedListBuilder<E, I, V> {
		protected CombinedListBuilder2(ObservableList<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<V> targetType,
			ObservableValue<T> arg2, Ternian combineNull) {
			super(source, parent, targetType, arg2, combineNull);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
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
		public ListDataFlow<E, I, V> build(Function<? super CombinedValues<? extends I>, ? extends V> combination, boolean combineNulls) {
			return new ObservableListImpl.CombinedListOp<>(getSource(), getParent(), getTargetType(), getResultArgs(), combination,
				combineNulls, getReverse(), isReverseNulls());
		}

		@Override
		public <U> CombinedListBuilder3<E, I, T, U, V> and(ObservableValue<U> arg3) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedListBuilder3<>(getSource(), getParent(), getTargetType(), getArg2(), combineNulls(getArg2()), arg3,
				Ternian.NONE);
		}

		@Override
		public <U> CombinedListBuilder3<E, I, T, U, V> and(ObservableValue<U> arg3, boolean combineNulls) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedListBuilder3<>(getSource(), getParent(), getTargetType(), getArg2(), combineNulls(getArg2()), arg3,
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
		protected CombinedListBuilder3(ObservableList<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<V> targetType,
			ObservableValue<T> arg2, Ternian combineArg2Nulls, ObservableValue<U> arg3, Ternian combineArg3Nulls) {
			super(source, parent, targetType, arg2, combineArg2Nulls, arg3, combineArg3Nulls);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
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
		public ListDataFlow<E, I, V> build(Function<? super CombinedValues<? extends I>, ? extends V> combination, boolean combineNulls) {
			return new ObservableListImpl.CombinedListOp<>(getSource(), getParent(), getTargetType(), getResultArgs(), combination,
				combineNulls, getReverse(), isReverseNulls());
		}

		@Override
		public <T2> CombinedListBuilderN<E, I, V> and(ObservableValue<T2> arg) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedListBuilderN<>(getSource(), getParent(), getTargetType(), getArg2(), combineNulls(getArg2()), getArg3(),
				combineNulls(getArg3()), arg, Ternian.NONE);
		}

		@Override
		public <T2> CombinedListBuilderN<E, I, V> and(ObservableValue<T2> arg, boolean combineNulls) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedListBuilderN<>(getSource(), getParent(), getTargetType(), getArg2(), combineNulls(getArg2()), getArg3(),
				combineNulls(getArg3()), arg, Ternian.of(combineNulls));
		}
	}

	/**
	 * A {@link ObservableList.CombinedListBuilder} for the combination of a list with one or more (typically at least 3) values. Use
	 * {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source list
	 * @param <I> An intermediate type
	 * @param <V> The type of elements in the resulting list
	 * @see ObservableList.ListDataFlow#combineWith(ObservableValue, TypeToken)
	 * @see ObservableList.CombinedListBuilder3#and(ObservableValue)
	 */
	class CombinedListBuilderN<E, I, V> extends CombinedCollectionBuilderN<E, I, V> implements CombinedListBuilder<E, I, V> {
		protected CombinedListBuilderN(ObservableList<E> source, AbstractDataFlow<E, ?, I> parent, TypeToken<V> targetType,
			ObservableValue<?> arg2, Ternian combineArg2Nulls, ObservableValue<?> arg3, Ternian combineArg3Nulls, ObservableValue<?> arg4,
			Ternian combineArg4Nulls) {
			super(source, parent, targetType, arg2, combineArg2Nulls, arg3, combineArg3Nulls, arg4, combineArg4Nulls);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
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
		public ListDataFlow<E, I, V> build(Function<? super CombinedValues<? extends I>, ? extends V> combination, boolean combineNulls) {
			return new ObservableListImpl.CombinedListOp<>(getSource(), getParent(), getTargetType(), getResultArgs(), combination,
				combineNulls, getReverse(), isReverseNulls());
		}
	}

	class ListModFilterBuilder<E, T> extends ModFilterBuilder<E, T> {
		public ListModFilterBuilder(ObservableList<E> source, CollectionDataFlow<E, ?, T> parent) {
			super(source, parent);
		}

		@Override
		protected ObservableList<E> getSource() {
			return (ObservableList<E>) super.getSource();
		}

		@Override
		public ListModFilterBuilder<E, T> immutable(String modMsg, boolean allowUpdates) {
			return (ListModFilterBuilder<E, T>) super.immutable(modMsg, allowUpdates);
		}

		@Override
		public ListModFilterBuilder<E, T> noAdd(String modMsg) {
			return (ListModFilterBuilder<E, T>) super.noAdd(modMsg);
		}

		@Override
		public ListModFilterBuilder<E, T> noRemove(String modMsg) {
			return (ListModFilterBuilder<E, T>) super.noRemove(modMsg);
		}

		@Override
		public ListModFilterBuilder<E, T> filterAdd(Function<? super T, String> messageFn) {
			return (ListModFilterBuilder<E, T>) super.filterAdd(messageFn);
		}

		@Override
		public ListModFilterBuilder<E, T> filterRemove(Function<? super T, String> messageFn) {
			return (ListModFilterBuilder<E, T>) super.filterRemove(messageFn);
		}

		@Override
		public ListDataFlow<E, T, T> build() {
			return new ObservableListImpl.ListModFilteredOp<>(getSource(), (AbstractDataFlow<E, ?, T>) getParent(), getImmutableMsg(),
				areUpdatesAllowed(), getAddMsg(), getRemoveMsg(), getAddMsgFn(), getRemoveMsgFn());
		}
	}

	class ObservableListIterator<E> extends ReversibleElementSpliterator.SpliteratorListIterator<E> {
		public ObservableListIterator(MutableObservableSpliterator<E> backing) {
			super(backing);
		}

		@Override
		protected MutableObservableElement<E> getCurrentElement() {
			return (MutableObservableElement<E>) super.getCurrentElement();
		}

		@Override
		public int nextIndex() {
			return getCurrentElement().getElementId().getElementsBefore() + getSpliteratorCursorOffset();
		}

		@Override
		public int previousIndex() {
			return getCurrentElement().getElementId().getElementsBefore() + getSpliteratorCursorOffset() - 1;
		}
	}
}
