package org.observe.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import org.observe.ObservableValue;
import org.qommons.Transaction;
import org.qommons.collect.BetterSet;
import org.qommons.collect.MutableElementSpliterator;

import com.google.common.reflect.TypeToken;

/**
 * A set whose content can be observed
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSet<E> extends ObservableCollection<E>, BetterSet<E> {
	@Override
	default Iterator<E> iterator() {
		return ObservableCollection.super.iterator();
	}

	@Override
	default MutableElementSpliterator<E> spliterator() {
		return ObservableCollection.super.spliterator();
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
	default <T> UniqueDataFlow<E, E, E> flow() {
		return new ObservableSetImpl.UniqueBaseFlow<>(this);
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
		ObservableSet<E> set = create(type, equivalence);
		set.addAll(values);
		return set.flow().immutable().collect();
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
	 * Turns an observable value containing an observable collection into the contents of the value
	 *
	 * @param collectionObservable The observable value
	 * @return A collection representing the contents of the value, or a zero-length collection when null
	 */
	public static <E> ObservableSet<E> flattenValue(ObservableValue<? extends ObservableSet<E>> collectionObservable) {
		return new ObservableSetImpl.FlattenedValueSet<>(collectionObservable);
	}

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
}
