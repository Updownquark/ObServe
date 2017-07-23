package org.observe.collect;

import java.util.Collection;

import org.observe.ObservableValue;
import org.qommons.Transaction;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.ImmutableIterator;
import org.qommons.collect.TransactableSet;

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
	default ElementSpliterator<E> spliterator() {
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

	/**
	 * @param <X> The type of the filtering collection
	 * @param collection The collection to filter this set's elements by
	 * @return A set containing all of this set's elements that are also present in the argument collection
	 */
	default <X> ObservableSet<E> intersect(ObservableCollection<X> collection){
		return new ObservableSetImpl.IntersectedSet<>(this, collection);
	}

	@Override
	default <T> UniqueDataFlow<E, E, E> flow() {
		return new ObservableSetImpl.UniqueBaseFlow<>(this);
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
