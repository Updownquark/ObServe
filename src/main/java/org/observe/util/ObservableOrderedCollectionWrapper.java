package org.observe.util;

import org.observe.Subscription;
import org.observe.collect.ObservableOrderedCollection;
import org.observe.collect.ObservableOrderedElement;

/**
 * Wraps an observable ordered collection
 *
 * @param <E> The type of the ordered collection
 */
public class ObservableOrderedCollectionWrapper<E> extends ObservableCollectionWrapper<E> implements ObservableOrderedCollection<E> {
	/** @param wrap The collection to wrap */
	public ObservableOrderedCollectionWrapper(ObservableOrderedCollection<E> wrap) {
		super(wrap, true);
	}

	/**
	 * @param wrap The collection to wrap
	 * @param modifiable Whether this collection can propagate modifications to the wrapped collection. If false, this collection will be
	 *            immutable.
	 */
	public ObservableOrderedCollectionWrapper(ObservableOrderedCollection<E> wrap, boolean modifiable) {
		super(wrap, modifiable);
	}

	@Override
	protected ObservableOrderedCollection<E> getWrapped() {
		return (ObservableOrderedCollection<E>) super.getWrapped();
	}

	@Override
	public Subscription onOrderedElement(java.util.function.Consumer<? super ObservableOrderedElement<E>> observer) {
		return getWrapped().onOrderedElement(observer);
	}
}
