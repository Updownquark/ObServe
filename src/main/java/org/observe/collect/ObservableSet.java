package org.observe.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;

import org.observe.ObservableValue;
import org.observe.collect.ObservableSetImpl.ConstantObservableSet;
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterSet;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A set whose content can be observed.
 *
 * See <a href="https://github.com/Updownquark/ObServe/wiki/ObservableCollection-API#observableset">the wiki</a> for more detail.
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSet<E> extends ObservableCollection<E>, BetterSet<E> {
	/** This class's type key */
	@SuppressWarnings("rawtypes")
	static TypeTokens.TypeKey<ObservableSet> TYPE_KEY = TypeTokens.get().keyFor(ObservableSet.class)
	.enableCompoundTypes(new TypeTokens.UnaryCompoundTypeCreator<ObservableSet>() {
		@Override
		public <P> TypeToken<? extends ObservableSet> createCompoundType(TypeToken<P> param) {
			return new TypeToken<ObservableSet<P>>() {}.where(new TypeParameter<P>() {}, param);
		}
	});
	/** This class's wildcard {@link TypeToken} */
	static TypeToken<ObservableSet<?>> TYPE = TYPE_KEY.parameterized();

	@Override
	default Iterator<E> iterator() {
		return ObservableCollection.super.iterator();
	}

	@Override
	default Spliterator<E> spliterator() {
		return BetterSet.super.spliterator();
	}

	@Override
	default ObservableSet<E> reverse() {
		return new ObservableSetImpl.ReversedSet<>(this);
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
	default boolean remove(Object o) {
		return ObservableCollection.super.remove(o);
	}

	@Override
	default boolean removeAll(Collection<?> c) {
		return ObservableCollection.super.removeAll(c);
	}

	@Override
	default boolean retainAll(Collection<?> c) {
		return ObservableCollection.super.retainAll(c);
	}

	@Override
	default ObservableSet<E> with(E... values) {
		ObservableCollection.super.with(values);
		return this;
	}

	@Override
	default ObservableSet<E> withAll(Collection<? extends E> values) {
		ObservableCollection.super.withAll(values);
		return this;
	}

	@Override
	default <T> DistinctDataFlow<E, E, E> flow() {
		return new ObservableSetImpl.DistinctBaseFlow<>(this);
	}

	/**
	 * @param <E> The type for the set
	 * @param type The type for the set
	 * @param values The values to be in the immutable set
	 * @return An immutable set with the given values
	 */
	static <E> ObservableSet<E> of(TypeToken<E> type, E... values) {
		return of(type, Arrays.asList(values));
	}

	/**
	 * @param <E> The type for the set
	 * @param type The type for the set
	 * @param values The values to be in the immutable set
	 * @return An immutable set with the given values
	 */
	static <E> ObservableSet<E> of(TypeToken<E> type, Collection<? extends E> values) {
		return of(type, Equivalence.DEFAULT, values);
	}

	/**
	 * @param <E> The type for the set
	 * @param type The type for the set
	 * @param equivalence The equivalence set to distinguish the values
	 * @param values The values to be in the immutable set
	 * @return An immutable set with the given values
	 */
	static <E> ObservableSet<E> of(TypeToken<E> type, Equivalence<? super E> equivalence, Collection<? extends E> values) {
		return new ConstantObservableSet<>(type, equivalence, ObservableCollection.<E> createDefaultBacking().withAll(values));
	}

	/**
	 * @param <E> The type for the set
	 * @param type The type for the set
	 * @return A new observable set with the given type
	 */
	static <E> ObservableSet<E> create(TypeToken<E> type) {
		return create(type, Equivalence.DEFAULT);
	}

	/**
	 * @param <E> The type for the set
	 * @param type The type for the set
	 * @param equivalence The equivalence set to distinguish the set's values
	 * @return A new observable set with the given type and equivalence
	 */
	static <E> ObservableSet<E> create(TypeToken<E> type, Equivalence<? super E> equivalence) {
		return ObservableCollection.create(type).flow().withEquivalence(equivalence).distinct().collect();
	}

	/**
	 * Turns an observable value containing an observable set into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A set representing the contents of the value, or a zero-length set when null
	 */
	public static <E> ObservableSet<E> flattenValue(ObservableValue<? extends ObservableSet<E>> collectionObservable) {
		return flattenValue(collectionObservable, Equivalence.DEFAULT);
	}

	/**
	 * Turns an observable value containing an observable set into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @param equivalence The equivalence for the set
	 * @return A set representing the contents of the value, or a zero-length set when null
	 */
	static <E> ObservableSet<E> flattenValue(ObservableValue<? extends ObservableSet<E>> collectionObservable,
		Equivalence<Object> equivalence) {
		return new ObservableSetImpl.FlattenedValueSet<>(collectionObservable, equivalence);
	}
}
