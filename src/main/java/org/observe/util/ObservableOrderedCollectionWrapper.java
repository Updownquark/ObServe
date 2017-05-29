package org.observe.util;

import org.observe.Subscription;
import org.observe.collect.ObservableIndexedCollection;
import org.observe.collect.ObservableOrderedElement;

/**
 * Wraps an observable ordered collection
 *
 * @param <E> The type of the ordered collection
 */
public class ObservableOrderedCollectionWrapper<E> extends ObservableCollectionWrapper<E> implements ObservableIndexedCollection<E> {
	/** @param wrap The collection to wrap */
	public ObservableOrderedCollectionWrapper(ObservableIndexedCollection<E> wrap) {
		super(wrap, true);
	}

	/**
	 * @param wrap The collection to wrap
	 * @param modifiable Whether this collection can propagate modifications to the wrapped collection. If false, this collection will be
	 *            immutable.
	 */
	public ObservableOrderedCollectionWrapper(ObservableIndexedCollection<E> wrap, boolean modifiable) {
		super(wrap, modifiable);
	}

	@Override
	protected ObservableIndexedCollection<E> getWrapped() {
		return (ObservableIndexedCollection<E>) super.getWrapped();
	}

	@Override
	public Subscription onOrderedElement(java.util.function.Consumer<? super ObservableOrderedElement<E>> observer) {
		return getWrapped().onOrderedElement(observer);
	}
}
