package org.observe.collect;

/**
 * A {@link ObservableCollectionEvent} for a {@link ObservableOrderedCollection} that additionally provides an {@link #getIndex() index}
 * 
 * @param <E> The type of values in the collection
 */
public class OrderedCollectionEvent<E> extends ObservableCollectionEvent<E> {
	private final int theIndex;

	/**
	 * @param elementId The ID of the element that was changed
	 * @param index The index of the element in the collection
	 * @param type The type of the change
	 * @param oldValue The old value for the element ({@link CollectionChangeType#set}-type only)
	 * @param newValue The new value for the element
	 * @param cause The cause of the change
	 */
	public OrderedCollectionEvent(ElementId elementId, int index, CollectionChangeType type, E oldValue, E newValue, Object cause) {
		super(elementId, type, oldValue, newValue, cause);
		theIndex = index;
	}

	/** @return The index of the element in the collection */
	public int getIndex() {
		return theIndex;
	}
}
