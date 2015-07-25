package org.observe.collect.impl;

import org.observe.DefaultObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;

import prisms.lang.Type;

class InternalObservableElementImpl<T> extends DefaultObservableValue<T> {
	private Observer<ObservableValueEvent<T>> theController;
	private final Type theType;
	private T theValue;

	InternalObservableElementImpl(Type type, T value) {
		theType = type;
		theValue = value;
		theController = control(null);
	}

	void set(T newValue) {
		T oldValue = theValue;
		theValue = newValue;
		theController.onNext(createChangeEvent(oldValue, newValue, null));
	}

	void remove() {
		theController.onCompleted(createChangeEvent(theValue, theValue, null));
	}

	@Override
	public Type getType() {
		return theType;
	}

	@Override
	public T get() {
		return theValue;
	}
}
