package org.observe.collect;

import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.qommons.Equalizer;
import org.qommons.Transaction;
import org.qommons.collect.Betterator;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.TransactableSet;

import com.google.common.reflect.TypeToken;

/**
 * A set whose content can be observed
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSet<E> extends ObservableCollection<E>, TransactableSet<E> {
	@Override
	default Betterator<E> iterator() {
		return ObservableCollection.super.iterator();
	}

	@Override
	ElementSpliterator<E> spliterator();

	/**
	 * @param o The object to get the equivalent of
	 * @return The object in this set whose value is equivalent to the given value
	 */
	default ObservableValue<E> equivalent(Object o) {
		return new ObservableSetImpl.ObservableSetEquivalentFinder<>(this, o);
	}

	@Override
	default E [] toArray() {
		return ObservableCollection.super.toArray();
	}

	@Override
	default <T> T [] toArray(T [] a) {
		return ObservableCollection.super.toArray(a);
	}

	default <X> ObservableSet<E> intersect(ObservableCollection<X> collection){
		return new ObservableSetImpl.IntersectedSet<>(this, collection);
	}

	// Filter/mapping

	@Override
	default <T> MappedSetOrCollectionBuilder<E, E, T> buildMap(TypeToken<T> type) {
		return new MappedSetOrCollectionBuilder<>(this, null, type);
	}

	/**
	 * Similar to {@link #filterMap(FilterMapDef, boolean)}, but produces a set, as {@link EquivalentFilterMapDef} instances can only be
	 * produced with the assertion that any map operations preserve the Set's uniqueness contract.
	 *
	 * @param <T> The type to map to
	 * @param filterMap The filter-map definition
	 * @param dynamic Whether the filtering on the set (if <code>filterMap</code> is {@link ObservableCollection.FilterMapDef#isFiltered()
	 *        filtered}) should be done dynamically or statically. Dynamic filtering allows for the possibility that changes to individual
	 *        elements in the collection may result in those elements passing or failing the filter. Static filtering uses the initial (on
	 *        subscription) value of the element to determine whether that element is included in the collection and this inclusion does not
	 *        change. Static filtering may offer potentially large performance improvements, particularly for filtering a small subset of a
	 *        large collection, but may cause incorrect results if the initial inclusion assumption is wrong.
	 * @return A set, filtered and mapped with the given definition
	 */
	default <T> ObservableSet<T> filterMap(EquivalentFilterMapDef<E, ?, T> filterMap, boolean dynamic) {
		return new ObservableSetImpl.FilterMappedSet<>(this, filterMap, dynamic);
	}

	@Override
	default ObservableSet<E> filter(Function<? super E, String> filter) {
		return (ObservableSet<E>) ObservableCollection.super.filter(filter);
	}

	@Override
	default <T> ObservableSet<T> filter(Class<T> type) {
		return (ObservableSet<T>) ObservableCollection.super.filter(type);
	}

	/**
	 * @param refresh The observable to re-fire events on
	 * @return A set whose elements fire additional value events when the given observable fires
	 */
	@Override
	default ObservableSet<E> refresh(Observable<?> refresh) {
		return new ObservableSetImpl.RefreshingSet<>(this, refresh);
	}

	/**
	 * @param refresh A function that supplies a refresh observable as a function of element value
	 * @return A collection whose values individually refresh when the observable returned by the given function fires
	 */
	@Override
	default ObservableSet<E> refreshEach(Function<? super E, Observable<?>> refresh) {
		return new ObservableSetImpl.ElementRefreshingSet<>(this, refresh);
	}

	@Override
	default ObservableSet<E> immutable(String modMsg) {
		return (ObservableSet<E>) ObservableCollection.super.immutable(modMsg);
	}

	@Override
	default ObservableSet<E> filterRemove(Function<? super E, String> filter) {
		return (ObservableSet<E>) ObservableCollection.super.filterRemove(filter);
	}

	@Override
	default ObservableSet<E> noRemove(String removeMsg) {
		return (ObservableSet<E>) ObservableCollection.super.noRemove(removeMsg);
	}

	@Override
	default ObservableSet<E> filterAdd(Function<? super E, String> filter) {
		return (ObservableSet<E>) ObservableCollection.super.filterAdd(filter);
	}

	@Override
	default ObservableSet<E> noAdd(String addMsg) {
		return (ObservableSet<E>) ObservableCollection.super.noAdd(addMsg);
	}

	@Override
	default ObservableSet<E> filterModification(Function<? super E, String> removeFilter, Function<? super E, String> addFilter) {
		return (ObservableSet<E>) ObservableCollection.super.filterModification(removeFilter, addFilter);
	}

	@Override
	default ObservableSet<E> cached(Observable<?> until) {
		return new SafeCachedObservableSet<>(this, until);
	}

	/**
	 * @param until The observable to end the set on
	 * @return A set that mirrors this set's values until the given observable fires a value, upon which the returned set's elements will be
	 *         removed and set subscriptions unsubscribed
	 */
	@Override
	default ObservableSet<E> takeUntil(Observable<?> until) {
		return new ObservableSetImpl.TakenUntilObservableSet<>(this, until, true);
	}

	/**
	 * @param until The observable to unsubscribe the set on
	 * @return A set that mirrors this set's values until the given observable fires a value, upon which the returned set's subscriptions
	 *         will be removed. Unlike {@link #takeUntil(Observable)} however, the returned set's elements will not be removed when the
	 *         observable fires.
	 */
	@Override
	default ObservableSet<E> unsubscribeOn(Observable<?> until) {
		return new ObservableSetImpl.TakenUntilObservableSet<>(this, until, true);
	}

	/**
	 * Turns an observable value containing an observable collection into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> ObservableSet<E> flattenValue(ObservableValue<? extends ObservableSet<E>> collectionObservable) {
		return new ObservableSetImpl.FlattenedValueSet<>(collectionObservable);
	}

	/*public static <E> ObservableSet<E> intersect(TypeToken<E> type, Collection<? extends ObservableSet<? extends E>> sets, Equalizer equalizer) {
		TypeToken<ObservableSet<? extends E>> setType = new TypeToken<ObservableSet<? extends E>>() {}.where(new TypeParameter<E>() {},
			type);
		ObservableCollection<ObservableSet<? extends E>> obSets = ObservableCollection.constant(setType,
			(Collection<ObservableSet<? extends E>>) sets);
		return combine(obSets, equalizer, sets.size(), Integer.MAX_VALUE);
	}

	public static <E> ObservableSet<E> union(ObservableCollection<? extends ObservableSet<? extends E>> sets, Equalizer equalizer) {
		return combine(sets, equalizer, 1, Integer.MAX_VALUE);
	}

	public static <E> ObservableSet<E> combine(ObservableCollection<? extends ObservableSet<? extends E>> sets, Equalizer equalizer,
		int minCount, int maxCount) {
		return new SetCombination<>(sets, equalizer, minCount, maxCount);
	}*/

	/**
	 * A default toString() method for set implementations to use
	 *
	 * @param set The set to print
	 * @return The string representation of the set
	 */
	public static String toString(ObservableSet<?> set) {
		StringBuilder ret = new StringBuilder("{");
		boolean first = true;
		try (Transaction t = set.lock(false, null)) {
			for(Object value : set) {
				if(!first) {
					ret.append(", ");
				} else
					first = false;
				ret.append(value);
			}
		}
		ret.append('}');
		return ret.toString();
	}

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param coll The collection with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> ObservableSet<T> constant(TypeToken<T> type, java.util.Collection<T> coll) {
		Set<T> modSet = new java.util.LinkedHashSet<>(coll);
		Set<T> constSet = java.util.Collections.unmodifiableSet(modSet);
		java.util.List<ObservableElement<T>> els = new java.util.ArrayList<>();
		class ConstantObservableSet implements PartialSetImpl<T> {
			@Override
			public Equalizer getEqualizer() {
				return Objects::equals;
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return ObservableValue.constant(TypeToken.of(CollectionSession.class), null);
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return Transaction.NONE;
			}

			@Override
			public boolean isSafe() {
				return true;
			}

			@Override
			public TypeToken<T> getType() {
				return type;
			}

			@Override
			public Subscription onElement(Consumer<? super ObservableElement<T>> observer) {
				for(ObservableElement<T> el : els)
					observer.accept(el);
				return () -> {
				};
			}

			@Override
			public int size() {
				return constSet.size();
			}

			@Override
			public Iterator<T> iterator() {
				return constSet.iterator();
			}
			@Override
			public boolean canRemove(Object value) {
				return false;
			}

			@Override
			public boolean canAdd(T value) {
				return false;
			}
		}
		ConstantObservableSet ret = new ConstantObservableSet();
		for(T value : constSet)
			els.add(new ObservableElement<T>() {
				@Override
				public TypeToken<T> getType() {
					return type;
				}

				@Override
				public T get() {
					return value;
				}

				@Override
				public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
					Observer.onNextAndFinish(observer, createInitialEvent(value, null));
					return () -> {
					};
				}
			});
		return ret;
	}

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param values The array with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> ObservableSet<T> constant(TypeToken<T> type, T... values) {
		return constant(type, java.util.Arrays.asList(values));
	}

	/**
	 * @param <T> The type of the collection
	 * @param coll The collection to turn into a set
	 * @param equivalence The equivalence set to use for uniqueness checking
	 * @return A set containing all unique elements of the given collection
	 */
	public static <T> ObservableSet<T> unique(ObservableCollection<T> coll, Equivalence<? super T> equivalence) {
		return new ObservableSetImpl.CollectionWrappingSet<>(coll, equivalence);
	}

	/**
	 * A filter-map builder that may produce either a plain {@link ObservableCollection} or a {@link ObservableSet}. It will produce a
	 * ObservableSet unless {#link #map(Function, boolean)} is called, producing a plain
	 * {@link ObservableCollection.MappedCollectionBuilder} that will produce a ObservableCollection as normal.
	 * {@link #mapEquiv(Function, boolean, Function, boolean)} may be used alternatively to preserve the uniqueness contract and produce a
	 * mapped ObservableSet.
	 *
	 * @param <E> The type of values in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of values in the mapped collection
	 */
	class MappedSetOrCollectionBuilder<E, I, T> extends MappedCollectionBuilder<E, I, T> {
		public MappedSetOrCollectionBuilder(ObservableSet<E> wrapped, MappedSetOrCollectionBuilder<E, ?, I> parent, TypeToken<T> type) {
			super(wrapped, parent, type);
		}

		@Override
		public ObservableSet<E> getCollection() {
			return (ObservableSet<E>) super.getCollection();
		}

		@Override
		protected MappedSetOrCollectionBuilder<E, ?, I> getParent() {
			return (MappedSetOrCollectionBuilder<E, ?, I>) super.getParent();
		}

		/**
		 * This method differs from its super method slightly in that it does not return this builder. Since no assumption can be made that
		 * a set mapped with the given function would retain its unique contract, this method returns a different builder that produces a
		 * plain {@link ObservableCollection} instead of a {@link ObservableSet}. If it is known that the given function preserves the
		 * uniqueness quality required of {@link Set} implementations and a {@link ObservableSet} is desired for the result, use
		 * {@link #mapEquiv(Function, boolean, Function, boolean)}.
		 *
		 * @param map The mapping function
		 * @param mapNulls Whether to apply the function to null values or simply pass them through to the mapped set as null values
		 * @return A plain {@link ObservableCollection} builder with the same properties as this builder, plus the given map
		 */
		@Override
		public MappedCollectionBuilder<E, I, T> map(Function<? super I, ? extends T> map, boolean mapNulls) {
			MappedCollectionBuilder<E, I, T> nonEquivBuilder = new MappedCollectionBuilder<>(getCollection(), getParent(), getType());
			if (getFilter() != null)
				nonEquivBuilder.filter(getFilter(), areNullsFiltered());
			if (getReverse() != null)
				nonEquivBuilder.withReverse(getReverse(), areNullsReversed());
			return nonEquivBuilder.map(map, mapNulls);
		}

		/**
		 * Similar to {@link #map(Function, boolean)}, but with the additional (unenforced) assertion that the given function applied to
		 * this set will produce a set of similarly unique values. Although this assertion is not enforced here and no exceptions will be
		 * thrown for violation of it, uniqueness is part of the contract of a {@link Set} that may be relied on by other code that may fail
		 * if that contract is not met.
		 *
		 * @param map The mapping function
		 * @param mapNulls Whether to apply the mapping function to null values or simply pass them through to the mapped set as null values
		 * @param reverse The reverse function to map from the results of a map operation back to objects that the wrapped set can
		 *        understand
		 * @param reverseNulls Whether to apply the reverse function to null values or simply pass them through to the wrapped set as null
		 *        values
		 * @return This builder
		 */
		public MappedSetOrCollectionBuilder<E, I, T> mapEquiv(Function<? super I, ? extends T> map, boolean mapNulls,
			Function<? super T, ? extends I> reverse, boolean reverseNulls) {
			Objects.requireNonNull(map);
			Objects.requireNonNull(reverse);
			return (MappedSetOrCollectionBuilder<E, I, T>) super.map(map, mapNulls).withReverse(reverse, reverseNulls);
		}

		@Override
		public <X> MappedSetOrCollectionBuilder<E, T, X> andThen(TypeToken<X> nextType) {
			return new MappedSetOrCollectionBuilder<>(getCollection(), this, nextType);
		}

		@Override
		public EquivalentFilterMapDef<E, I, T> toDef() {
			EquivalentFilterMapDef<E, ?, I> parent = getParent() == null ? null : getParent().toDef();
			TypeToken<I> intermediate = parent == null ? (TypeToken<I>) getCollection().getType() : parent.destType;
			return new EquivalentFilterMapDef<>(getCollection().getType(), intermediate, getType(), parent, getFilter(), areNullsFiltered(),
				getMap(), areNullsMapped(), getReverse(), areNullsReversed());
		}

		@Override
		public ObservableSet<T> build(boolean dynamic) {
			return getCollection().filterMap(toDef(), dynamic);
		}
	}

	/**
	 * The type of {@link ObservableCollection.FilterMapDef} produced by {@link ObservableSet.MappedSetOrCollectionBuilder}s when the
	 * uniqueness contract is preserved.
	 *
	 * @param <E> The type of values in the source collection
	 * @param <I> Intermediate type
	 * @param <T> The type of values in the mapped collection
	 */
	class EquivalentFilterMapDef<E, I, T> extends FilterMapDef<E, I, T> {
		public EquivalentFilterMapDef(TypeToken<E> sourceType, TypeToken<I> intermediateType, TypeToken<T> type,
			EquivalentFilterMapDef<E, ?, I> parent, Function<? super I, String> filter, boolean filterNulls,
			Function<? super I, ? extends T> map, boolean mapNulls, Function<? super T, ? extends I> reverse, boolean reverseNulls) {
			super(sourceType, intermediateType, type, parent, filter, filterNulls, map, mapNulls, reverse, reverseNulls);
		}
	}
}
