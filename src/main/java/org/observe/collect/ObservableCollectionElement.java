package org.observe.collect;

import org.qommons.collect.CollectionElement;

/**
 * A {@link CollectionElement} that additionally provides the {@link ElementId element ID} for the element
 * 
 * @param <E> The type of value in the element
 */
public interface ObservableCollectionElement<E> extends CollectionElement<E> {
	/** @return The ID of this element */
	ElementId getElementId();
}
