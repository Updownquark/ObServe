package org.observe.collect;

import org.qommons.AbstractCausable;

public class ObservableCollectionEvent<E> extends AbstractCausable {
	private final CollectionChangeType theType;
	private final E theOldValue;
	private final E theNewValue;

	public ObservableCollectionEvent(CollectionChangeType type, E oldValue, E newValue, Object cause) {
		super(cause);
		theType = type;
		theOldValue = oldValue;
		theNewValue = newValue;
	}

	public CollectionChangeType getType() {
		return theType;
	}

	public E getOldValue() {
		return theOldValue;
	}

	public E getNewValue() {
		return theNewValue;
	}

	public boolean isInitial() {
		return theType == CollectionChangeType.add;
	}

	public boolean isFinal() {
		return theType == CollectionChangeType.remove;
	}
}
