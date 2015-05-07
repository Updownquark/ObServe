package org.observe.collect;

import static org.observe.ObservableDebug.debug;

import java.util.function.Consumer;
import java.util.function.Predicate;

import org.observe.ObservableValue;

public interface ObservableOrderedReversibleCollection<E> extends ObservableOrderedCollection<E> {
	Runnable onElementReverse(Consumer<? super OrderedObservableElement<E>> onElement);
	Iterable<E> descending();

	default ObservableOrderedReversibleCollection<E> reverse() {
	}

	/* Overridden for performance.  get() is linear in the super, constant time here */
	@Override
	default ObservableValue<E> findLast(Predicate<E> filter) {
		return debug(new OrderedReversibleCollectionFinder<>(this, filter, false)).from("findLast", this).using("filter", filter).get();
	}

	/* Overridden for performance.  get() is linear in the super, constant time here */
	@Override
	default ObservableValue<E> last() {
		return debug(new OrderedReversibleCollectionFinder<>(this, value -> true, false)).from("last", this).get();
	}
}
