package org.observe.collect;

import static org.observe.ObservableDebug.debug;

import java.util.RandomAccess;
import java.util.function.Predicate;

import org.observe.ObservableValue;

/**
 * An ObservableList whose contents can be accessed by index with constant (or near-constant) time
 *
 * @param <E> The type of values stored in the list
 */
public interface ObservableRandomAccessList<E> extends ObservableList<E>, RandomAccess {
	/* Overridden for performance.  get() is linear in the super, constant time here */
	@Override
	default ObservableValue<E> findLast(Predicate<E> filter) {
		return debug(new RandomAccessFinder<>(this, filter, false)).from("findLast", this).using("filter", filter).get();
	}

	/* Overridden for performance.  get() is linear in the super, constant time here */
	@Override
	default ObservableValue<E> last() {
		return debug(new RandomAccessFinder<>(this, value -> true, false)).from("last", this).get();
	}
}
