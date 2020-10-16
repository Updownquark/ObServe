package org.observe.collect;

import java.util.Collection;

import org.observe.ObservableValueEvent;
import org.qommons.collect.ElementId;

import com.google.common.reflect.TypeToken;

/**
 * An event representing a change to an {@link ObservableCollection}
 *
 * @param <E> The type of values in the collection
 */
public class ObservableCollectionEvent<E> extends ObservableValueEvent<E> {
	private final ElementId theElementId;
	private final int theIndex;
	private final CollectionChangeType theType;

	/**
	 * @param elementId The ID of the element that was changed
	 * @param valueType The type of the value, for validation
	 * @param index The index of the element in the collection
	 * @param type The type of the change
	 * @param oldValue The old value for the element ({@link CollectionChangeType#set}-type only)
	 * @param newValue The new value for the element
	 * @param causes The causes of the change
	 */
	public ObservableCollectionEvent(ElementId elementId, TypeToken<E> valueType, int index, CollectionChangeType type, E oldValue,
		E newValue, Object... causes) {
		super(valueType, type == CollectionChangeType.add, oldValue, newValue, causes);
		if (index < 0)
			throw new IndexOutOfBoundsException("" + index);
		theElementId = elementId;
		theIndex = index;
		theType = type;
	}

	/**
	 * @param elementId The ID of the element that was changed
	 * @param valueType The type of the value, for validation
	 * @param index The index of the element in the collection
	 * @param type The type of the change
	 * @param oldValue The old value for the element ({@link CollectionChangeType#set}-type only)
	 * @param newValue The new value for the element
	 * @param causes The causes of the change
	 */
	public ObservableCollectionEvent(ElementId elementId, TypeToken<E> valueType, int index, CollectionChangeType type, E oldValue,
		E newValue, Collection<?> causes) {
		this(elementId, valueType, index, type, oldValue, newValue, causes.toArray());
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
			str.append("+:").append(getNewValue());
			break;
		case remove:
			str.append("-:").append(getOldValue());
			break;
		case set:
			str.append(':').append(getOldValue()).append("->").append(getNewValue());
			break;
		}
		return str.toString();
	}
}
