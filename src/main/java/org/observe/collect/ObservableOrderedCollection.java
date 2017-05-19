package org.observe.collect;

import java.util.LinkedHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.qommons.Ternian;
import org.qommons.Transaction;
import org.qommons.TriFunction;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * An indexed collection whose content can be observed. All {@link ObservableCollectionEvent}s fired by this collection will be instances of
 * {@link OrderedCollectionEvent}. In addition, it is guaranteed that the {@link OrderedCollectionEvent#getIndex() index} of an element
 * given to the observer passed to {@link #subscribe(Consumer)} will be less than or equal to the number of uncompleted elements previously
 * passed to the observer. This means that, for example, the first element passed to an observer will always be index 0. The second may be 0
 * or 1. If one of these is then completed, the next element may be 0 or 1 as well.
 *
 * @param <E> The type of element in the collection
 */
public interface ObservableOrderedCollection<E> extends ObservableCollection<E> {
	/**
	 * @param observer The listener to be notified of changes to the collection
	 * @return The subscription to call when the calling code is no longer interested in this collection
	 */
	CollectionSubscription subscribeOrdered(Consumer<? super OrderedCollectionEvent<? extends E>> observer);

	@Override
	default CollectionSubscription subscribe(Consumer<? super ObservableCollectionEvent<? extends E>> observer) {
		return subscribeOrdered(observer);
	}

	/**
	 * @return An observable that returns null whenever any elements in this collection are added, removed or changed. The order of events
	 *         as reported by this observable may not be the same as their occurrence in the collection. Any discrepancy will be resolved
	 *         when the transaction ends.
	 */
	@Override
	default Observable<? extends OrderedCollectionChangeEvent<E>> changes() {
		return new ObservableOrderedCollectionImpl.OrderedCollectionChangesObservable<>(this);
	}

	// Ordered collections need to know the indexes of their elements in a somewhat efficient way, so these index methods make sense here

	/**
	 * @param index The index of the element to get
	 * @return The element of this collection at the given index
	 */
	E get(int index);

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the first position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	int indexOf(Object value);

	/**
	 * @param value The value to get the index of in this collection
	 * @return The index of the last position in this collection occupied by the given value, or &lt; 0 if the element does not exist in
	 *         this collection
	 */
	int lastIndexOf(Object value);

	/** @return The last value in this collection, or null if the collection is empty */
	default E last() {
		try (Transaction t = lock(false, null)) {
			return get(size() - 1);
		}
	}

	@Override
	default ObservableOrderedCollection<E> withEquivalence(Equivalence<? super E> otherEquiv) {
		return new ObservableOrderedCollectionImpl.EquivalenceSwitchedOrderedCollection<>(this, otherEquiv);
	}

	@Override
	default ObservableOrderedCollection<E> filter(Function<? super E, String> filter) {
		return (ObservableOrderedCollection<E>) ObservableCollection.super.filter(filter);
	}

	@Override
	default <T> ObservableOrderedCollection<T> filter(Class<T> type) {
		return (ObservableOrderedCollection<T>) ObservableCollection.super.filter(type);
	}

	@Override
	default <T> ObservableOrderedCollection<T> map(Function<? super E, T> map) {
		return (ObservableOrderedCollection<T>) ObservableCollection.super.map(map);
	}

	@Override
	default <T> MappedOrderedCollectionBuilder<E, E, T> buildMap(TypeToken<T> type) {
		return new MappedOrderedCollectionBuilder<>(this, null, type);
	}

	@Override
	default <T> ObservableOrderedCollection<T> filterMap(FilterMapDef<E, ?, T> filterMap) {
		return new ObservableOrderedCollectionImpl.FilterMappedOrderedCollection<>(this, filterMap);
	}

	@Override
	default <T> ObservableOrderedCollection<T> flatMapValues(TypeToken<T> type,
		Function<? super E, ? extends ObservableValue<? extends T>> map) {
		TypeToken<ObservableValue<? extends T>> collectionType;
		if (type == null) {
			collectionType = (TypeToken<ObservableValue<? extends T>>) TypeToken.of(map.getClass())
				.resolveType(Function.class.getTypeParameters()[1]);
			if (!collectionType.isAssignableFrom(new TypeToken<ObservableOrderedCollection<T>>() {}))
				collectionType = new TypeToken<ObservableValue<? extends T>>() {};
		} else {
			collectionType = new TypeToken<ObservableValue<? extends T>>() {}.where(new TypeParameter<T>() {}, type);
		}
		return flattenValues(this.<ObservableValue<? extends T>> buildMap(collectionType).map(map, false).build());
	}

	/**
	 * Shorthand for {@link #flatten(ObservableOrderedCollection) flatten}({@link #map(Function) map}(Function))
	 *
	 * @param <T> The type of the values produced
	 * @param type The type of the values produced
	 * @param map The value producer
	 * @return A collection whose values are the accumulation of all those produced by applying the given function to all of this
	 *         collection's values
	 */
	default <T> ObservableOrderedCollection<T> flatMapOrdered(TypeToken<T> type,
		Function<? super E, ? extends ObservableOrderedCollection<? extends T>> map) {
		TypeToken<ObservableOrderedCollection<? extends T>> collectionType;
		if (type == null) {
			collectionType = (TypeToken<ObservableOrderedCollection<? extends T>>) TypeToken.of(map.getClass())
				.resolveType(Function.class.getTypeParameters()[1]);
			if (!collectionType.isAssignableFrom(new TypeToken<ObservableOrderedCollection<T>>() {}))
				collectionType = new TypeToken<ObservableOrderedCollection<? extends T>>() {};
		} else {
			collectionType = new TypeToken<ObservableOrderedCollection<? extends T>>() {}.where(new TypeParameter<T>() {}, type);
		}
		return flatten(this.<ObservableOrderedCollection<? extends T>> buildMap(collectionType).map(map, false).build());
	}

	@Override
	default <T, V> CombinedOrderedCollectionBuilder2<E, T, V> combineWith(ObservableValue<T> arg, TypeToken<V> targetType) {
		return new CombinedOrderedCollectionBuilder2<>(this, arg, targetType);
	}

	@Override
	default <V> ObservableOrderedCollection<V> combine(CombinedCollectionDef<E, V> combination) {
		return new ObservableOrderedCollectionImpl.CombinedOrderedCollection<>(this, combination);
	}

	@Override
	default OrderedModFilterBuilder<E> filterModification() {
		return new OrderedModFilterBuilder<>(this);
	}

	@Override
	default ObservableOrderedCollection<E> filterModification(ModFilterDef<E> filter) {
		return new ObservableOrderedCollectionImpl.ModFilteredOrderedCollection<>(this, filter);
	}

	@Override
	default ObservableOrderedCollection<E> cached(Observable<?> until) {
		return new ObservableOrderedCollectionImpl.CachedOrderedCollection<>(this, until);
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableOrderedCollection<E> refresh(Observable<?> refresh) {
		return new ObservableOrderedCollectionImpl.RefreshingOrderedCollection<>(this, refresh);
	}

	@Override
	default ObservableOrderedCollection<E> refreshEach(Function<? super E, Observable<?>> refire) {
		return new ObservableOrderedCollectionImpl.ElementRefreshingOrderedCollection<>(this, refire);
	}

	@Override
	default ObservableOrderedCollection<E> takeUntil(Observable<?> until) {
		return new ObservableOrderedCollectionImpl.TakenUntilOrderedCollection<>(this, until, true);
	}

	@Override
	default ObservableOrderedCollection<E> unsubscribeOn(Observable<?> until) {
		return new ObservableOrderedCollectionImpl.TakenUntilOrderedCollection<>(this, until, false);
	}

	/**
	 * Turns a collection of observable values into a collection composed of those holders' values
	 *
	 * @param <E> The type of elements held in the values
	 * @param collection The collection to flatten
	 * @return The flattened collection
	 */
	public static <E> ObservableOrderedCollection<E> flattenValues(
		ObservableOrderedCollection<? extends ObservableValue<? extends E>> collection) {
		return new ObservableOrderedCollectionImpl.FlattenedOrderedValuesCollection<>(collection);
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> ObservableOrderedCollection<E> flattenValue(
		ObservableValue<? extends ObservableOrderedCollection<? extends E>> collectionObservable) {
		return new ObservableOrderedCollectionImpl.FlattenedOrderedValueCollection<>(collectionObservable);
	}

	/**
	 * Flattens a collection of ordered collections
	 *
	 * @param <E> The super-type of all collections in the wrapping collection
	 * @param list The collection to flatten
	 * @return A collection containing all elements of all collections in the outer collection
	 */
	public static <E> ObservableOrderedCollection<E> flatten(
		ObservableOrderedCollection<? extends ObservableOrderedCollection<? extends E>> list) {
		return new ObservableOrderedCollectionImpl.FlattenedOrderedCollection<>(list);
	}

	/**
	 * A {@link ObservableCollection.MappedCollectionBuilder} that builds an {@link ObservableOrderedCollection}
	 *
	 * @param <E> The type of values in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of values in the mapped collection
	 */
	class MappedOrderedCollectionBuilder<E, I, T> extends MappedCollectionBuilder<E, I, T> {
		protected MappedOrderedCollectionBuilder(ObservableOrderedCollection<E> wrapped, MappedOrderedCollectionBuilder<E, ?, I> parent,
			TypeToken<T> type) {
			super(wrapped, parent, type);
		}

		@Override
		protected ObservableOrderedCollection<E> getCollection() {
			return (ObservableOrderedCollection<E>) super.getCollection();
		}

		@Override
		public MappedOrderedCollectionBuilder<E, I, T> filter(Function<? super I, String> filter, boolean filterNulls) {
			return (MappedOrderedCollectionBuilder<E, I, T>) super.filter(filter, filterNulls);
		}

		@Override
		public MappedOrderedCollectionBuilder<E, I, T> map(Function<? super I, ? extends T> map, boolean mapNulls) {
			return (MappedOrderedCollectionBuilder<E, I, T>) super.map(map, mapNulls);
		}

		@Override
		public MappedOrderedCollectionBuilder<E, I, T> withReverse(Function<? super T, ? extends I> reverse, boolean reverseNulls) {
			return (MappedOrderedCollectionBuilder<E, I, T>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public ObservableOrderedCollection<T> build() {
			return (ObservableOrderedCollection<T>) super.build();
		}

		@Override
		public <X> MappedOrderedCollectionBuilder<E, T, X> andThen(TypeToken<X> nextType) {
			if (getMap() == null && !getCollection().getType().equals(getType()))
				throw new IllegalStateException("Type-mapped collection builder with no map defined");
			return new MappedOrderedCollectionBuilder<>(getCollection(), this, nextType);
		}
	}

	/**
	 * A {@link ObservableCollection.CombinedCollectionBuilder} that builds an {@link ObservableOrderedCollection}
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <V> The type of elements in the resulting collection
	 * @see ObservableOrderedCollection#combineWith(ObservableValue, TypeToken)
	 * @see ObservableOrderedCollection.CombinedOrderedCollectionBuilder3#and(ObservableValue)
	 */
	interface CombinedOrderedCollectionBuilder<E, V> extends CombinedCollectionBuilder<E, V> {
		@Override
		<T> CombinedOrderedCollectionBuilder<E, V> and(ObservableValue<T> arg);

		@Override
		<T> CombinedOrderedCollectionBuilder<E, V> and(ObservableValue<T> arg, boolean combineNulls);

		@Override
		CombinedOrderedCollectionBuilder<E, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends E> reverse,
			boolean reverseNulls);

		@Override
		ObservableOrderedCollection<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination);

		@Override
		CombinedCollectionDef<E, V> toDef(Function<? super CombinedValues<? extends E>, ? extends V> combination);
	}

	/**
	 * A {@link ObservableOrderedCollection.CombinedOrderedCollectionBuilder} for the combination of a collection with a single value. Use
	 * {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <T> The type of the combined value
	 * @param <V> The type of elements in the resulting collection
	 * @see ObservableOrderedCollection#combineWith(ObservableValue, TypeToken)
	 */
	class CombinedOrderedCollectionBuilder2<E, T, V> extends CombinedCollectionBuilder2<E, T, V>
	implements CombinedOrderedCollectionBuilder<E, V> {
		public CombinedOrderedCollectionBuilder2(ObservableOrderedCollection<E> collection, ObservableValue<T> arg2,
			TypeToken<V> targetType) {
			super(collection, arg2, targetType);
		}

		@Override
		public ObservableOrderedCollection<E> getSource() {
			return (ObservableOrderedCollection<E>) super.getSource();
		}

		@Override
		public CombinedOrderedCollectionBuilder2<E, T, V> combineNulls(boolean combineNulls) {
			return (CombinedOrderedCollectionBuilder2<E, T, V>) super.combineNulls(combineNulls);
		}

		@Override
		public CombinedOrderedCollectionBuilder2<E, T, V> combineCollectionNulls(boolean combineNulls) {
			return (CombinedOrderedCollectionBuilder2<E, T, V>) super.combineCollectionNulls(combineNulls);
		}

		@Override
		public CombinedOrderedCollectionBuilder2<E, T, V> combineNullArg2(boolean combineNulls) {
			return (CombinedOrderedCollectionBuilder2<E, T, V>) super.combineNullArg2(combineNulls);
		}

		@Override
		public CombinedOrderedCollectionBuilder2<E, T, V> withReverse(BiFunction<? super V, ? super T, ? extends E> reverse,
			boolean reverseNulls) {
			return (CombinedOrderedCollectionBuilder2<E, T, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public CombinedOrderedCollectionBuilder2<E, T, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends E> reverse,
			boolean reverseNulls) {
			return (CombinedOrderedCollectionBuilder2<E, T, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public ObservableOrderedCollection<V> build(BiFunction<? super E, ? super T, ? extends V> combination) {
			return (ObservableOrderedCollection<V>) super.build(combination);
		}

		@Override
		public ObservableOrderedCollection<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return (ObservableOrderedCollection<V>) super.build(combination);
		}

		@Override
		public <U> CombinedOrderedCollectionBuilder3<E, T, U, V> and(ObservableValue<U> arg3) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedOrderedCollectionBuilder3<>(this, arg3, Ternian.NONE);
		}

		@Override
		public <U> CombinedOrderedCollectionBuilder3<E, T, U, V> and(ObservableValue<U> arg3, boolean combineNulls) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedOrderedCollectionBuilder3<>(this, arg3, Ternian.of(combineNulls));
		}
	}

	/**
	 * A {@link ObservableOrderedCollection.CombinedOrderedCollectionBuilder} for the combination of a collection with 2 values. Use
	 * {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <T> The type of the first combined value
	 * @param <U> The type of the second combined value
	 * @param <V> The type of elements in the resulting collection
	 * @see ObservableOrderedCollection#combineWith(ObservableValue, TypeToken)
	 * @see ObservableOrderedCollection.CombinedOrderedCollectionBuilder2#and(ObservableValue)
	 */
	class CombinedOrderedCollectionBuilder3<E, T, U, V> extends CombinedCollectionBuilder3<E, T, U, V>
	implements CombinedOrderedCollectionBuilder<E, V> {
		public CombinedOrderedCollectionBuilder3(CombinedOrderedCollectionBuilder2<E, T, V> combine2, ObservableValue<U> arg3,
			Ternian combineNulls) {
			super(combine2, arg3, combineNulls);
		}

		@Override
		public ObservableOrderedCollection<E> getSource() {
			return (ObservableOrderedCollection<E>) getCombine2().getSource();
		}

		@Override
		public CombinedOrderedCollectionBuilder3<E, T, U, V> withReverse(TriFunction<? super V, ? super T, ? super U, ? extends E> reverse,
			boolean reverseNulls) {
			return (CombinedOrderedCollectionBuilder3<E, T, U, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public CombinedOrderedCollectionBuilder3<E, T, U, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends E> reverse,
			boolean reverseNulls) {
			return (CombinedOrderedCollectionBuilder3<E, T, U, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public ObservableOrderedCollection<V> build(TriFunction<? super E, ? super T, ? super U, ? extends V> combination) {
			return (ObservableOrderedCollection<V>) super.build(combination);
		}

		@Override
		public ObservableOrderedCollection<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return (ObservableOrderedCollection<V>) super.build(combination);
		}

		@Override
		public <T2> CombinedOrderedCollectionBuilderN<E, V> and(ObservableValue<T2> arg) {
			if (getCombine2().getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedOrderedCollectionBuilderN<>(this).and(arg);
		}

		@Override
		public <T2> CombinedOrderedCollectionBuilder<E, V> and(ObservableValue<T2> arg, boolean combineNulls) {
			if (getCombine2().getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedOrderedCollectionBuilderN<>(this).and(arg, combineNulls);
		}
	}

	/**
	 * A {@link ObservableOrderedCollection.CombinedOrderedCollectionBuilder} for the combination of a collection with one or more
	 * (typically at least 3) values. Use {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <V> The type of elements in the resulting collection
	 * @see ObservableOrderedCollection#combineWith(ObservableValue, TypeToken)
	 * @see ObservableOrderedCollection.CombinedOrderedCollectionBuilder3#and(ObservableValue)
	 */
	class CombinedOrderedCollectionBuilderN<E, V> extends CombinedCollectionBuilderN<E, V>
	implements CombinedOrderedCollectionBuilder<E, V> {
		public CombinedOrderedCollectionBuilderN(CombinedOrderedCollectionBuilder3<E, ?, ?, V> combine3) {
			super(combine3);
		}

		@Override
		public CombinedOrderedCollectionBuilder<E, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends E> reverse,
			boolean reverseNulls) {
			return (CombinedOrderedCollectionBuilder<E, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public <T> CombinedOrderedCollectionBuilderN<E, V> and(ObservableValue<T> arg) {
			return (CombinedOrderedCollectionBuilderN<E, V>) super.and(arg);
		}

		@Override
		public <T> CombinedOrderedCollectionBuilderN<E, V> and(ObservableValue<T> arg, boolean combineNull) {
			return (CombinedOrderedCollectionBuilderN<E, V>) super.and(arg, combineNull);
		}

		@Override
		public ObservableOrderedCollection<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return (ObservableOrderedCollection<V>) super.build(combination);
		}

		@Override
		public CombinedCollectionDef<E, V> toDef(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return new CombinedCollectionDef<>(getTargetType(), addArgs(new LinkedHashMap<>(2)), combination, areCollectionNullsCombined(),
				getReverse(), areNullsReversed(), false);
		}
	}

	/**
	 * Builds a modification filter that may prevent certain kinds of modification to the collection
	 *
	 * @param <E> The type of elements in the collection
	 */
	class OrderedModFilterBuilder<E> extends ModFilterBuilder<E> {
		public OrderedModFilterBuilder(ObservableOrderedCollection<E> collection) {
			super(collection);
		}

		@Override
		protected ObservableOrderedCollection<E> getSource() {
			return (ObservableOrderedCollection<E>) super.getSource();
		}

		@Override
		public OrderedModFilterBuilder<E> immutable(String modMsg) {
			return (OrderedModFilterBuilder<E>) super.immutable(modMsg);
		}

		@Override
		public OrderedModFilterBuilder<E> noAdd(String modMsg) {
			return (OrderedModFilterBuilder<E>) super.noAdd(modMsg);
		}

		@Override
		public OrderedModFilterBuilder<E> noRemove(String modMsg) {
			return (OrderedModFilterBuilder<E>) super.noRemove(modMsg);
		}

		@Override
		public OrderedModFilterBuilder<E> filterAdd(Function<? super E, String> messageFn) {
			return (OrderedModFilterBuilder<E>) super.filterAdd(messageFn);
		}

		@Override
		public OrderedModFilterBuilder<E> filterRemove(Function<? super E, String> messageFn) {
			return (OrderedModFilterBuilder<E>) super.filterRemove(messageFn);
		}

		@Override
		public ObservableOrderedCollection<E> build() {
			return (ObservableOrderedCollection<E>) super.build();
		}
	}
}
