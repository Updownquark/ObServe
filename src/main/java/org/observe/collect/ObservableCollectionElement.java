package org.observe.collect;

import org.qommons.collect.CollectionElement;

public interface ObservableCollectionElement<E> extends CollectionElement<E> {
	ElementId getElementId();
}
