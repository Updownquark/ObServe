package org.observe.collect;

import java.util.NavigableSet;
import java.util.function.Predicate;

/**
 * A sorted set whose content can be observed. This set is immutable in that none of its methods, including {@link java.util.Set} methods,
 * can modify its content (Set modification methods will throw {@link UnsupportedOperationException}). All {@link ObservableElement}s
 * returned by this observable will be instances of {@link OrderedObservableElement}.
 *
 * @param <E> The type of element in the set
 */
public interface ObservableSortedSet<E> extends ObservableSet<E>, NavigableSet<E> {
	/**
	 * @param filter The filter function
	 * @return A sorted set containing all elements of this collection that pass the given test
	 */
	@Override
	default ObservableSortedSet<E> filter(Predicate<? super E> filter) {
		// TODO
		return null;
	}
}
