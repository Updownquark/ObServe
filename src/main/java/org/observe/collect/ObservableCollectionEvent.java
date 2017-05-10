package org.observe.collect;

import org.qommons.AbstractCausable;

public class ObservableCollectionEvent<E> extends AbstractCausable {
	private final Object theElementId;
	private final CollectionChangeType theType;
	private final E theOldValue;
	private final E theNewValue;

	public ObservableCollectionEvent(Object elId, CollectionChangeType type, E oldValue, E newValue, Object cause) {
		super(cause);
		theElementId = elId;
		theType = type;
		theOldValue = oldValue;
		theNewValue = newValue;
	}

	public Object getElementId() {
		return theElementId;
	}

	public CollectionChangeType getType() {
		return theType;
	}

	public int getDiff() {
		switch (theType) {
		case add:
			return 1;
		case remove:
			return -1;
		default:
			return 0;
		}
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
