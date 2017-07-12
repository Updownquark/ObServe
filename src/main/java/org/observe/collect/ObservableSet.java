package org.observe.collect;

import org.observe.ObservableValue;
import org.qommons.Transaction;
import org.qommons.collect.ImmutableIterator;
import org.qommons.collect.TransactableSet;

import com.google.common.reflect.TypeToken;

/**
 * A set whose content can be observed
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSet<E> extends ObservableCollection<E>, TransactableSet<E> {
	@Override
	default ImmutableIterator<E> iterator() {
		return ObservableCollection.super.iterator();
	}

	@Override
	default ObservableElementSpliterator<E> spliterator() {
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

	/**
	 * @param <X> The type of the filtering collection
	 * @param collection The collection to filter this set's elements by
	 * @return A set containing all of this set's elements that are also present in the argument collection
	 */
	default <X> ObservableSet<E> intersect(ObservableCollection<X> collection){
		return new ObservableSetImpl.IntersectedSet<>(this, collection);
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

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param equivalence The equivalence set for the set's uniqueness
	 * @param coll The collection with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> ObservableSet<T> constant(TypeToken<T> type, Equivalence<? super T> equivalence, java.util.Collection<T> coll) {
		return new ObservableSetImpl.ConstantObservableSet<>(type, equivalence, coll);
	}

	/**
	 * @param <T> The type of the collection
	 * @param type The run-time type of the collection
	 * @param equivalence The equivalence set for the set's uniqueness
	 * @param values The array with elements to wrap
	 * @return A collection containing the given elements that cannot be changed
	 */
	public static <T> ObservableSet<T> constant(TypeToken<T> type, Equivalence<? super T> equivalence, T... values) {
		return constant(type, equivalence, java.util.Arrays.asList(values));
	}

	@Override
	default <T> UniqueDataFlow<E, E, E> flow() {
		return new ObservableSetImpl.UniqueBaseFlow<>(this);
	}
}
