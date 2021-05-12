package org.observe.supertest.map;

import org.observe.supertest.CollectionOpType;
import org.observe.supertest.collect.CollectionLinkElement;

public class ExpectedMultiMapValueOperation<K, V> {
	private final CollectionLinkElement<V, K> theKeyElement;
	private final CollectionLinkElement<V, V> theValueElement;
	private final CollectionOpType theType;
	private final V theOldValue;
	private final V theNewValue;

	public ExpectedMultiMapValueOperation(CollectionLinkElement<V, K> keyElement, CollectionLinkElement<V, V> valueElement,
		CollectionOpType type, V oldValue, V newValue) {
		theKeyElement = keyElement;
		theValueElement = valueElement;
		theType = type;
		theOldValue = oldValue;
		theNewValue = newValue;
	}

	public CollectionLinkElement<V, K> getKeyElement() {
		return theKeyElement;
	}

	public CollectionLinkElement<V, V> getValueElement() {
		return theValueElement;
	}

	public CollectionOpType getType() {
		return theType;
	}

	public V getOldValue() {
		return theOldValue;
	}

	public V getNewValue() {
		return theNewValue;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder().append(theKeyElement.getValue()).append(':');
		switch (theType) {
		case move:
			throw new IllegalStateException();
		case add:
			str.append('+').append(theNewValue);
			break;
		case remove:
			str.append('-').append(theOldValue);
			break;
		case set:
			str.append(theOldValue).append("->").append(theNewValue);
			break;
		}
		return str.toString();
	}
}
