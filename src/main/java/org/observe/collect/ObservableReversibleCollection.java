package org.observe.collect;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.qommons.Ternian;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ReversibleCollection;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * An observable ordered collection that can be reversed
 *
 * @param <E> The type of elements in the collection
 */
public interface ObservableReversibleCollection<E> extends ObservableOrderedCollection<E>, ReversibleCollection<E> {
	/**
	 * Same as {@link #subscribeOrdered(Consumer)}, except that the elements currently present in this collection are given to the observer
	 * in reverse order
	 *
	 * @param observer The listener to be notified of changes to the collection
	 * @return The subscription to call when the calling code is no longer interested in this collection
	 */
	CollectionSubscription subscribeReverse(Consumer<? super OrderedCollectionEvent<? extends E>> observer);

	@Override
	default ObservableReversibleSpliterator<E> spliterator() {
		return spliterator(true);
	}

	@Override
	ObservableReversibleSpliterator<E> spliterator(boolean fromStart);

	/** @return A collection that is identical to this one, but with its elements reversed */
	@Override
	default ObservableReversibleCollection<E> reverse() {
		return new ObservableReversibleCollectionImpl.ObservableReversedCollection<>(this);
	}

	/**
	 * Overridden to return an {@link ObservableCollectionElement}. Also with the additional contract that this method must return the
	 * <b>first</b> matching element.
	 *
	 * @see org.qommons.collect.ReversibleCollection#elementFor(Object, boolean)
	 */
	@Override
	default ObservableCollectionElement<E> elementFor(Object value, boolean first) {
		return (ObservableCollectionElement<E>) ReversibleCollection.super.elementFor(value, first);
	}

	/**
	 * @param search The test to search for elements that pass
	 * @param onElement The action to take on the first passing element in the collection
	 * @param first Whether to find the first matching element or the last one
	 * @return Whether an element was found that passed the test
	 * @see #find(Predicate, Consumer, boolean)
	 */
	default boolean findObservableElement(Predicate<? super E> search, Consumer<? super ObservableCollectionElement<? extends E>> onElement,
		boolean first) {
		return ReversibleCollection.super.find(search, (Consumer<? super CollectionElement<? extends E>>) onElement, first);
	}

	/**
	 * @param search The test to search for elements that pass
	 * @param onElement The action to take on all passing elements in the collection
	 * @param fromStart Whether to start from the beginning or the end of the collection
	 * @return The number of elements found that passed the test
	 * @see #findAll(Predicate, Consumer, boolean)
	 */
	default int findAllObservableElements(Predicate<? super E> search, Consumer<? super ObservableCollectionElement<? extends E>> onElement,
		boolean fromStart) {
		return ReversibleCollection.super.findAll(search, (Consumer<? super CollectionElement<? extends E>>) onElement, fromStart);
	}

	/* Overridden for performance */
	/**
	 * @param test The test to find passing elements for
	 * @param def Supplies a default value for the observable result when no elements in this collection pass the test
	 * @param first true to always use the first element passing the test, false to always use the last element
	 * @return An observable value containing a value in this collection passing the given test
	 */
	@Override
	default ObservableValue<E> find(Predicate<? super E> test, Supplier<? extends E> def, boolean first) {
		return new ObservableReversibleCollectionImpl.ReversibleCollectionFinder<>(this, test, def, first);
	}

	@Override
	default E last() {
		try (Transaction t = lock(false, null)) {
			Iterator<E> iter = descending().iterator();
			return iter.hasNext() ? iter.next() : null;
		}
	}

	@Override
	default ObservableReversibleCollection<E> withEquivalence(Equivalence<? super E> otherEquiv) {
		return new ObservableReversibleCollectionImpl.EquivalenceSwitchedReversibleCollection<>(this, otherEquiv);
	}

	@Override
	default ObservableReversibleCollection<E> filter(Function<? super E, String> filter) {
		return (ObservableReversibleCollection<E>) ObservableOrderedCollection.super.filter(filter);
	}

	@Override
	default <T> ObservableReversibleCollection<T> filter(Class<T> type) {
		return (ObservableReversibleCollection<T>) ObservableOrderedCollection.super.filter(type);
	}

	@Override
	default <T> ObservableReversibleCollection<T> map(Function<? super E, T> map) {
		return (ObservableReversibleCollection<T>) ObservableOrderedCollection.super.map(map);
	}

	@Override
	default <T> MappedReversibleCollectionBuilder<E, E, T> buildMap(TypeToken<T> type) {
		return new MappedReversibleCollectionBuilder<>(this, null, type);
	}

	@Override
	default <T> ObservableReversibleCollection<T> filterMap(FilterMapDef<E, ?, T> filterMap) {
		return new ObservableReversibleCollectionImpl.FilterMappedReversibleCollection<>(this, filterMap);
	}

	@Override
	default <T> ObservableReversibleCollection<T> flatMapValues(TypeToken<T> type,
		Function<? super E, ? extends ObservableValue<? extends T>> map) {
		TypeToken<ObservableValue<? extends T>> collectionType;
		if (type == null) {
			collectionType = (TypeToken<ObservableValue<? extends T>>) TypeToken.of(map.getClass())
				.resolveType(Function.class.getTypeParameters()[1]);
			if (!collectionType.isAssignableFrom(new TypeToken<ObservableReversibleCollection<T>>() {}))
				collectionType = new TypeToken<ObservableValue<? extends T>>() {};
		} else {
			collectionType = new TypeToken<ObservableValue<? extends T>>() {}.where(new TypeParameter<T>() {}, type);
		}
		return flattenValues(this.<ObservableValue<? extends T>> buildMap(collectionType).map(map, false).build());
	}

	/**
	 * Shorthand for {@link #flatten(ObservableReversibleCollection) flatten}({@link #map(Function) map}(Function))
	 *
	 * @param <T> The type of the values produced
	 * @param type The type of the values produced
	 * @param map The value producer
	 * @return A collection whose values are the accumulation of all those produced by applying the given function to all of this
	 *         collection's values
	 */
	default <T> ObservableReversibleCollection<T> flatMapReversible(TypeToken<T> type,
		Function<? super E, ? extends ObservableReversibleCollection<? extends T>> map) {
		TypeToken<ObservableReversibleCollection<? extends T>> collectionType;
		if (type == null) {
			collectionType = (TypeToken<ObservableReversibleCollection<? extends T>>) TypeToken.of(map.getClass())
				.resolveType(Function.class.getTypeParameters()[1]);
			if (!collectionType.isAssignableFrom(new TypeToken<ObservableReversibleCollection<T>>() {}))
				collectionType = new TypeToken<ObservableReversibleCollection<? extends T>>() {};
		} else {
			collectionType = new TypeToken<ObservableReversibleCollection<? extends T>>() {}.where(new TypeParameter<T>() {}, type);
		}
		return flatten(this.<ObservableReversibleCollection<? extends T>> buildMap(collectionType).map(map, false).build());
	}

	@Override
	default <T, V> CombinedReversibleCollectionBuilder2<E, T, V> combineWith(ObservableValue<T> arg, TypeToken<V> targetType) {
		return new CombinedReversibleCollectionBuilder2<>(this, arg, targetType);
	}

	@Override
	default <V> ObservableReversibleCollection<V> combine(CombinedCollectionDef<E, V> combination) {
		return new ObservableReversibleCollectionImpl.CombinedReversibleCollection<>(this, combination);
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A collection whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableReversibleCollection<E> refresh(Observable<?> refresh) {
		return new ObservableReversibleCollectionImpl.RefreshingReversibleCollection<>(this, refresh);
	}

	@Override
	default ObservableReversibleCollection<E> refreshEach(Function<? super E, Observable<?>> refire) {
		return new ObservableReversibleCollectionImpl.ElementRefreshingReversibleCollection<>(this, refire);
	}

	@Override
	default ReversibleModFilterBuilder<E> filterModification() {
		return new ReversibleModFilterBuilder<>(this);
	}

	@Override
	default ObservableReversibleCollection<E> filterModification(ModFilterDef<E> filter) {
		return new ObservableReversibleCollectionImpl.ModFilteredReversibleCollection<>(this, filter);
	}

	@Override
	default ObservableReversibleCollection<E> cached(Observable<?> until) {
		return new ObservableReversibleCollectionImpl.CachedReversibleCollection<>(this, until);
	}

	@Override
	default ObservableReversibleCollection<E> takeUntil(Observable<?> until) {
		return new ObservableReversibleCollectionImpl.TakenUntilReversibleCollection<>(this, until, true);
	}

	@Override
	default ObservableReversibleCollection<E> unsubscribeOn(Observable<?> until) {
		return new ObservableReversibleCollectionImpl.TakenUntilReversibleCollection<>(this, until, false);
	}

	/**
	 * Turns a collection of observable values into a collection composed of those holders' values
	 *
	 * @param <E> The type of elements held in the values
	 * @param collection The collection to flatten
	 * @return The flattened collection
	 */
	public static <E> ObservableReversibleCollection<E> flattenValues(
		ObservableReversibleCollection<? extends ObservableValue<? extends E>> collection) {
		return new ObservableReversibleCollectionImpl.FlattenedReversibleValuesCollection<>(collection);
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> ObservableReversibleCollection<E> flattenValue(
		ObservableValue<? extends ObservableReversibleCollection<? extends E>> collectionObservable) {
		return new ObservableReversibleCollectionImpl.FlattenedReversibleValueCollection<>(collectionObservable);
	}

	/**
	 * Flattens a collection of ordered collections
	 *
	 * @param <E> The super-type of all collections in the wrapping collection
	 * @param list The collection to flatten
	 * @return A collection containing all elements of all collections in the outer collection
	 */
	public static <E> ObservableReversibleCollection<E> flatten(
		ObservableReversibleCollection<? extends ObservableReversibleCollection<? extends E>> list) {
		return new ObservableReversibleCollectionImpl.FlattenedReversibleCollection<>(list);
	}

	/**
	 * A {@link ObservableCollection.MappedCollectionBuilder} that builds an {@link ObservableReversibleCollection}
	 *
	 * @param <E> The type of values in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of values in the mapped collection
	 */
	class MappedReversibleCollectionBuilder<E, I, T> extends MappedOrderedCollectionBuilder<E, I, T> {
		protected MappedReversibleCollectionBuilder(ObservableReversibleCollection<E> wrapped,
			MappedReversibleCollectionBuilder<E, ?, I> parent, TypeToken<T> type) {
			super(wrapped, parent, type);
		}

		@Override
		protected ObservableReversibleCollection<E> getCollection() {
			return (ObservableReversibleCollection<E>) super.getCollection();
		}

		@Override
		public MappedReversibleCollectionBuilder<E, I, T> filter(Function<? super I, String> filter, boolean filterNulls) {
			return (MappedReversibleCollectionBuilder<E, I, T>) super.filter(filter, filterNulls);
		}

		@Override
		public MappedReversibleCollectionBuilder<E, I, T> map(Function<? super I, ? extends T> map, boolean mapNulls) {
			return (MappedReversibleCollectionBuilder<E, I, T>) super.map(map, mapNulls);
		}

		@Override
		public MappedReversibleCollectionBuilder<E, I, T> withReverse(Function<? super T, ? extends I> reverse, boolean reverseNulls) {
			return (MappedReversibleCollectionBuilder<E, I, T>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public ObservableReversibleCollection<T> build() {
			return (ObservableReversibleCollection<T>) super.build();
		}

		@Override
		public <X> MappedReversibleCollectionBuilder<E, T, X> andThen(TypeToken<X> nextType) {
			if (getMap() == null && !getCollection().getType().equals(getType()))
				throw new IllegalStateException("Type-mapped collection builder with no map defined");
			return new MappedReversibleCollectionBuilder<>(getCollection(), this, nextType);
		}
	}

	/**
	 * A {@link ObservableCollection.CombinedCollectionBuilder} that builds an {@link ObservableReversibleCollection}
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <V> The type of elements in the resulting collection
	 * @see ObservableOrderedCollection#combineWith(ObservableValue, TypeToken)
	 * @see ObservableOrderedCollection.CombinedOrderedCollectionBuilder3#and(ObservableValue)
	 */
	interface CombinedReversibleCollectionBuilder<E, V> extends CombinedOrderedCollectionBuilder<E, V> {
		@Override
		<T> CombinedReversibleCollectionBuilder<E, V> and(ObservableValue<T> arg);

		@Override
		<T> CombinedReversibleCollectionBuilder<E, V> and(ObservableValue<T> arg, boolean combineNulls);

		@Override
		CombinedReversibleCollectionBuilder<E, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends E> reverse,
			boolean reverseNulls);

		@Override
		ObservableReversibleCollection<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination);

		@Override
		CombinedCollectionDef<E, V> toDef(Function<? super CombinedValues<? extends E>, ? extends V> combination);
	}

	/**
	 * A {@link ObservableReversibleCollection.CombinedReversibleCollectionBuilder} for the combination of a collection with a single value.
	 * Use {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <T> The type of the combined value
	 * @param <V> The type of elements in the resulting collection
	 * @see ObservableReversibleCollection#combineWith(ObservableValue, TypeToken)
	 */
	class CombinedReversibleCollectionBuilder2<E, T, V> extends CombinedOrderedCollectionBuilder2<E, T, V>
	implements CombinedReversibleCollectionBuilder<E, V> {
		public CombinedReversibleCollectionBuilder2(ObservableReversibleCollection<E> collection, ObservableValue<T> arg2,
			TypeToken<V> targetType) {
			super(collection, arg2, targetType);
		}

		@Override
		public ObservableReversibleCollection<E> getSource() {
			return (ObservableReversibleCollection<E>) super.getSource();
		}

		@Override
		public CombinedReversibleCollectionBuilder2<E, T, V> combineNulls(boolean combineNulls) {
			return (CombinedReversibleCollectionBuilder2<E, T, V>) super.combineNulls(combineNulls);
		}

		@Override
		public CombinedReversibleCollectionBuilder2<E, T, V> combineCollectionNulls(boolean combineNulls) {
			return (CombinedReversibleCollectionBuilder2<E, T, V>) super.combineCollectionNulls(combineNulls);
		}

		@Override
		public CombinedReversibleCollectionBuilder2<E, T, V> combineNullArg2(boolean combineNulls) {
			return (CombinedReversibleCollectionBuilder2<E, T, V>) super.combineNullArg2(combineNulls);
		}

		@Override
		public CombinedReversibleCollectionBuilder2<E, T, V> withReverse(BiFunction<? super V, ? super T, ? extends E> reverse,
			boolean reverseNulls) {
			return (CombinedReversibleCollectionBuilder2<E, T, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public CombinedReversibleCollectionBuilder2<E, T, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends E> reverse,
			boolean reverseNulls) {
			return (CombinedReversibleCollectionBuilder2<E, T, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public ObservableReversibleCollection<V> build(BiFunction<? super E, ? super T, ? extends V> combination) {
			return (ObservableReversibleCollection<V>) super.build(combination);
		}

		@Override
		public ObservableReversibleCollection<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return (ObservableReversibleCollection<V>) super.build(combination);
		}

		@Override
		public <U> CombinedReversibleCollectionBuilder3<E, T, U, V> and(ObservableValue<U> arg3) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedReversibleCollectionBuilder3<>(this, arg3, Ternian.NONE);
		}

		@Override
		public <U> CombinedReversibleCollectionBuilder3<E, T, U, V> and(ObservableValue<U> arg3, boolean combineNulls) {
			if (getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedReversibleCollectionBuilder3<>(this, arg3, Ternian.of(combineNulls));
		}
	}

	/**
	 * A {@link ObservableReversibleCollection.CombinedReversibleCollectionBuilder} for the combination of a collection with 2 values. Use
	 * {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <T> The type of the first combined value
	 * @param <U> The type of the second combined value
	 * @param <V> The type of elements in the resulting collection
	 * @see ObservableReversibleCollection#combineWith(ObservableValue, TypeToken)
	 * @see ObservableReversibleCollection.CombinedReversibleCollectionBuilder2#and(ObservableValue)
	 */
	class CombinedReversibleCollectionBuilder3<E, T, U, V> extends CombinedOrderedCollectionBuilder3<E, T, U, V>
	implements CombinedReversibleCollectionBuilder<E, V> {
		public CombinedReversibleCollectionBuilder3(CombinedReversibleCollectionBuilder2<E, T, V> combine2, ObservableValue<U> arg3,
			Ternian combineNulls) {
			super(combine2, arg3, combineNulls);
		}

		@Override
		public ObservableReversibleCollection<E> getSource() {
			return (ObservableReversibleCollection<E>) getCombine2().getSource();
		}

		@Override
		public CombinedReversibleCollectionBuilder3<E, T, U, V> withReverse(
			TriFunction<? super V, ? super T, ? super U, ? extends E> reverse, boolean reverseNulls) {
			return (CombinedReversibleCollectionBuilder3<E, T, U, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public CombinedReversibleCollectionBuilder3<E, T, U, V> withReverse(
			Function<? super CombinedValues<? extends V>, ? extends E> reverse, boolean reverseNulls) {
			return (CombinedReversibleCollectionBuilder3<E, T, U, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public ObservableReversibleCollection<V> build(TriFunction<? super E, ? super T, ? super U, ? extends V> combination) {
			return (ObservableReversibleCollection<V>) super.build(combination);
		}

		@Override
		public ObservableReversibleCollection<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return (ObservableReversibleCollection<V>) super.build(combination);
		}

		@Override
		public <T2> CombinedReversibleCollectionBuilderN<E, V> and(ObservableValue<T2> arg) {
			if (getCombine2().getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedReversibleCollectionBuilderN<>(this).and(arg);
		}

		@Override
		public <T2> CombinedReversibleCollectionBuilder<E, V> and(ObservableValue<T2> arg, boolean combineNulls) {
			if (getCombine2().getReverse() != null)
				throw new IllegalStateException("Reverse cannot be applied to a collection builder that will be AND-ed");
			return new CombinedReversibleCollectionBuilderN<>(this).and(arg, combineNulls);
		}
	}

	/**
	 * A {@link ObservableReversibleCollection.CombinedReversibleCollectionBuilder} for the combination of a collection with one or more
	 * (typically at least 3) values. Use {@link #and(ObservableValue)} to combine with additional values.
	 *
	 * @param <E> The type of elements in the source collection
	 * @param <V> The type of elements in the resulting collection
	 * @see ObservableReversibleCollection#combineWith(ObservableValue, TypeToken)
	 * @see ObservableReversibleCollection.CombinedReversibleCollectionBuilder3#and(ObservableValue)
	 */
	class CombinedReversibleCollectionBuilderN<E, V> extends CombinedOrderedCollectionBuilderN<E, V>
	implements CombinedReversibleCollectionBuilder<E, V> {
		public CombinedReversibleCollectionBuilderN(CombinedReversibleCollectionBuilder3<E, ?, ?, V> combine3) {
			super(combine3);
		}

		@Override
		public CombinedReversibleCollectionBuilder<E, V> withReverse(Function<? super CombinedValues<? extends V>, ? extends E> reverse,
			boolean reverseNulls) {
			return (CombinedReversibleCollectionBuilder<E, V>) super.withReverse(reverse, reverseNulls);
		}

		@Override
		public <T> CombinedReversibleCollectionBuilderN<E, V> and(ObservableValue<T> arg) {
			return (CombinedReversibleCollectionBuilderN<E, V>) super.and(arg);
		}

		@Override
		public <T> CombinedReversibleCollectionBuilderN<E, V> and(ObservableValue<T> arg, boolean combineNull) {
			return (CombinedReversibleCollectionBuilderN<E, V>) super.and(arg, combineNull);
		}

		@Override
		public ObservableReversibleCollection<V> build(Function<? super CombinedValues<? extends E>, ? extends V> combination) {
			return (ObservableReversibleCollection<V>) super.build(combination);
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
	class ReversibleModFilterBuilder<E> extends OrderedModFilterBuilder<E> {
		public ReversibleModFilterBuilder(ObservableReversibleCollection<E> collection) {
			super(collection);
		}

		@Override
		protected ObservableReversibleCollection<E> getSource() {
			return (ObservableReversibleCollection<E>) super.getSource();
		}

		@Override
		public ReversibleModFilterBuilder<E> immutable(String modMsg) {
			return (ReversibleModFilterBuilder<E>) super.immutable(modMsg);
		}

		@Override
		public ReversibleModFilterBuilder<E> noAdd(String modMsg) {
			return (ReversibleModFilterBuilder<E>) super.noAdd(modMsg);
		}

		@Override
		public ReversibleModFilterBuilder<E> noRemove(String modMsg) {
			return (ReversibleModFilterBuilder<E>) super.noRemove(modMsg);
		}

		@Override
		public ReversibleModFilterBuilder<E> filterAdd(Function<? super E, String> messageFn) {
			return (ReversibleModFilterBuilder<E>) super.filterAdd(messageFn);
		}

		@Override
		public ReversibleModFilterBuilder<E> filterRemove(Function<? super E, String> messageFn) {
			return (ReversibleModFilterBuilder<E>) super.filterRemove(messageFn);
		}

		@Override
		public ObservableReversibleCollection<E> build() {
			return (ObservableReversibleCollection<E>) super.build();
		}
	}
}
