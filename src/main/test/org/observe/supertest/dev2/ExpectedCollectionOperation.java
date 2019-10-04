package org.observe.supertest.dev2;

import org.observe.collect.CollectionChangeType;

public class ExpectedCollectionOperation<S, T> {
	private final CollectionLinkElement<S, T> theElement;
	private final CollectionChangeType theType;
	private final T theOldValue;
	private final T theValue;

	private String isRejected;

	public ExpectedCollectionOperation(CollectionLinkElement<S, T> element, CollectionChangeType type, T oldValue, T value) {
		theElement = element;
		theType = type;
		theOldValue = oldValue;
		theValue = value;
	}

	public CollectionLinkElement<S, T> getElement() {
		return theElement;
	}

	public CollectionChangeType getType() {
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
}
