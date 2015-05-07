package org.observe.collect;

import java.util.function.Predicate;

/**
 * Finds something in an {@link ObservableOrderedCollection}. More performant for backward searching.
 *
 * @param <E> The type of value to find
 */
public class OrderedReversibleCollectionFinder<E> extends OrderedCollectionFinder<E> {
	OrderedReversibleCollectionFinder(ObservableOrderedReversibleCollection<E> collection, Predicate<? super E> filter, boolean forward) {
		super(collection, filter, forward);
	}

	/** @return The collection that this finder searches */
	@Override
	public ObservableOrderedReversibleCollection<E> getCollection() {
		return (ObservableOrderedReversibleCollection<E>) super.getCollection();
	}

	@Override
	public E get() {
		if(isForward()) {
			return super.get();
		} else {
			for(E element : getCollection().descending()) {
				if(getFilter().test(element))
					return element;
			}
			return null;
		}
	}
}
