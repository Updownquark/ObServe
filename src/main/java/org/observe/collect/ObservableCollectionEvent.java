package org.observe.collect;

import org.qommons.AbstractCausable;

/**
 * An event representing a change to an {@link ObservableCollection}
 *
 * @param <E> The type of values in the collection
 */
public class ObservableCollectionEvent<E> extends AbstractCausable {
	private final ElementId theElementId;
	private final int theIndex;
	private final CollectionChangeType theType;
	private final E theOldValue;
	private final E theNewValue;

	/**
	 * @param elementId The ID of the element that was changed
	 * @param index The index of the element in the collection
	 * @param type The type of the change
	 * @param oldValue The old value for the element ({@link CollectionChangeType#set}-type only)
	 * @param newValue The new value for the element
	 * @param cause The cause of the change
	 */
	public ObservableCollectionEvent(ElementId elementId, int index, CollectionChangeType type, E oldValue, E newValue, Object cause) {
		super(cause);
		theElementId = elementId;
		theIndex = index;
		theType = type;
		theOldValue = oldValue;
		theNewValue = newValue;
	}

	/** @return The ID of the element that was changed */
	public ElementId getElementId() {
		return theElementId;
	}

	/** @return The index of the element in the collection */
	public int getIndex() {
		return theIndex;
	}

	/** @return The type of the change */
	public CollectionChangeType getType() {
		return theType;
	}

	/** @return 1 for {@link CollectionChangeType#add}, -1 for {@link CollectionChangeType#remove}, 0 otherwise */
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

	/** @return The old value for the element ({@link CollectionChangeType#set}-type only) */
	public E getOldValue() {
		return theOldValue;
	}

	/** @return The new value for the element */
	public E getNewValue() {
		return theNewValue;
	}

	/** @return true for type {@link CollectionChangeType#add}, false otherwise */
	public boolean isInitial() {
		return theType == CollectionChangeType.add;
	}

	/** @return true for type {@link CollectionChangeType#remove}, false otherwise */
	public boolean isFinal() {
		return theType == CollectionChangeType.remove;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append('[').append(theElementId).append(']');
		switch (theType) {
		case add:
			str.append("+:").append(theNewValue);
			break;
		case remove:
			str.append("-:").append(theOldValue);
			break;
		case set:
			str.append(':').append(theOldValue).append("->").append(theNewValue);
			break;
		}
		return str.toString();
	}
}
