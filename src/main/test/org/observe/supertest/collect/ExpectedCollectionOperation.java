package org.observe.supertest.collect;

/**
 * An operation on an {@link ObservableCollectionLink}
 * 
 * @param <S> The type of the source link of the link this operation is on
 * @param <T> The type of the link this operation is on
 */
public class ExpectedCollectionOperation<S, T> {
	/** The type of a collection operation */
	public enum CollectionOpType {
		/** An add operation */
		add,
		/** A remove operation */
		remove,
		/** A set operation */
		set,
		/** A move operation */
		move;
	}

	private final CollectionLinkElement<S, T> theElement;
	private final ExpectedCollectionOperation.CollectionOpType theType;
	private final T theOldValue;
	private final T theValue;

	/**
	 * @param element The element that is the subject of the operation
	 * @param type The type of the operation
	 * @param oldValue The previous value associated with the element
	 * @param value The new value associated with the element
	 */
	public ExpectedCollectionOperation(CollectionLinkElement<S, T> element, CollectionOpType type, T oldValue, T value) {
		theElement = element;
		theType = type;
		theOldValue = oldValue;
		theValue = value;
	}

	/** @return The element that is the subject of the operation */
	public CollectionLinkElement<S, T> getElement() {
		return theElement;
	}

	/** @return The type of the operation */
	public CollectionOpType getType() {
		return theType;
	}

	/** @return The previous value associated with the element */
	public T getOldValue() {
		return theOldValue;
	}

	/** @return The new value associated with the element */
	public T getValue() {
		return theValue;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		switch (theType) {
		case add:
			str.append('+').append(theValue);
			break;
		case remove:
			str.append('-').append(theValue);
			break;
		case set:
			str.append(theOldValue).append("->").append(theValue);
			break;
		case move:
			str.append("move:").append(theValue);
			break;
		}
		return str.toString();
	}
}
