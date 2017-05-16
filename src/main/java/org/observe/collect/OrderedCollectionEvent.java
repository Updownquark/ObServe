package org.observe.collect;

public class OrderedCollectionEvent<E> extends ObservableCollectionEvent<E> {
	private final int theIndex;

	public OrderedCollectionEvent(ElementId elementId, int index, CollectionChangeType type, E oldValue, E newValue, Object cause) {
		super(elementId, type, oldValue, newValue, cause);
		theIndex = index;
	}

	public int getIndex() {
		return theIndex;
	}
}
