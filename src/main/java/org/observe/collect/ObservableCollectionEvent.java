package org.observe.collect;

import java.util.Collection;

import org.observe.ObservableValueEvent;
import org.qommons.collect.ElementId;

/**
 * An event representing a change to an {@link ObservableCollection}
 *
 * @param <E> The type of values in the collection
 */
public class ObservableCollectionEvent<E> extends ObservableValueEvent<E> {
	private final ElementId theElementId;
	private final int theIndex;
	private final CollectionChangeType theType;
	private final boolean isMove;

	/**
	 * @param elementId The ID of the element that was changed
	 * @param index The index of the element in the collection
	 * @param type The type of the change
	 * @param move Whether this event represents either the removal of an element in preparation for a move, or the re-addition of an
	 *        element that was just removed in the same move operation
	 * @param oldValue The old value for the element ({@link CollectionChangeType#set}-type only)
	 * @param newValue The new value for the element
	 * @param causes The causes of the change
	 */
	public ObservableCollectionEvent(ElementId elementId, int index, CollectionChangeType type, boolean move, E oldValue, E newValue,
		Object... causes) {
		super(type == CollectionChangeType.add, oldValue, newValue, causes);
		theElementId = elementId;
		theIndex = index;
		theType = type;
		isMove = move;
		checkIndex(index);
	}

	/**
	 * Checks the index for this type and throws an exception if invalid
	 *
	 * @param index The index passed to the constructor
	 */
	protected void checkIndex(int index) {
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
	}

	/**
	 * @param elementId The ID of the element that was changed
	 * @param index The index of the element in the collection
	 * @param type The type of the change
	 * @param move Whether this event represents either the removal of an element in preparation for a move, or the re-addition of an
	 *        element that was just removed in the same move operation
	 * @param oldValue The old value for the element ({@link CollectionChangeType#set}-type only)
	 * @param newValue The new value for the element
	 * @param causes The causes of the change
	 */
	public ObservableCollectionEvent(ElementId elementId, int index, CollectionChangeType type, boolean move, E oldValue, E newValue,
		Collection<?> causes) {
		this(elementId, index, type, move, oldValue, newValue, causes.toArray());
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

	/**
	 * @return Whether this event represents either the removal of an element in preparation for a move, or the re-addition of an element
	 *         that was just removed in the same move operation
	 */
	public boolean isMove() {
		return isMove;
	}

	/** @return true for type {@link CollectionChangeType#remove}, false otherwise */
	public boolean isFinal() {
		return theType == CollectionChangeType.remove;
	}

	@Override
	public boolean isUpdate() {
		return theType == CollectionChangeType.set && super.isUpdate();
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append('[').append(theIndex).append(']');
		switch (theType) {
		case add:
			str.append("+:").append(getNewValue());
			break;
		case remove:
			str.append("-:").append(getOldValue());
			break;
		case set:
			str.append(':').append(getOldValue()).append("->").append(getNewValue());
			break;
		}
		if (isMove)
			str.append("(move)");
		return str.toString();
	}
}
