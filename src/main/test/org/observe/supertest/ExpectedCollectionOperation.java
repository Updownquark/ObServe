package org.observe.supertest;

import org.observe.supertest.ObservableCollectionLink.CollectionOpType;

public class ExpectedCollectionOperation<S, T> {
	private final CollectionLinkElement<S, T> theElement;
	private final CollectionOpType theType;
	private final T theOldValue;
	private final T theValue;

	private String isRejected;

	public ExpectedCollectionOperation(CollectionLinkElement<S, T> element, CollectionOpType type, T oldValue, T value) {
		theElement = element;
		theType = type;
		theOldValue = oldValue;
		theValue = value;
	}

	public CollectionLinkElement<S, T> getElement() {
		return theElement;
	}

	public CollectionOpType getType() {
		return theType;
	}

	public T getOldValue() {
		return theOldValue;
	}

	public T getValue() {
		return theValue;
	}

	public void reject(String msg) {
		isRejected = msg;
	}

	public String isRejected() {
		return isRejected;
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
