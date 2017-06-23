package org.observe.collect;

import java.util.function.Consumer;

import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementSpliterator;

public interface MutableObservableSpliterator<E> extends ObservableElementSpliterator<E>, ElementSpliterator<E> {
	@Override
	default MutableObservableSpliterator<E> trySplit() {
		return null;
	}

	boolean tryAdvanceMutableElement(Consumer<? super MutableObservableElement<E>> action);

	default void forEachMutableElement(Consumer<? super MutableObservableElement<E>> action) {
		while (tryAdvanceMutableElement(action)) {
		}
	}

	@Override
	default boolean tryAdvanceElement(Consumer<? super CollectionElement<E>> action) {
		return tryAdvanceMutableElement(action);
	}

	@Override
	default void forEachElement(Consumer<? super CollectionElement<E>> action) {
		forEachMutableElement(action);
	}

	@Override
	default boolean tryAdvanceObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
		return tryAdvanceMutableElement(action);
	}

	@Override
	default void forEachObservableElement(Consumer<? super ObservableCollectionElement<E>> action) {
		forEachMutableElement(action);
	}

	@Override
	default boolean tryAdvance(Consumer<? super E> action) {
		return tryAdvanceObservableElement(el -> action.accept(el.get()));
	}

	@Override
	default void forEachRemaining(Consumer<? super E> action) {
		forEachObservableElement(el -> action.accept(el.get()));
	}
}
