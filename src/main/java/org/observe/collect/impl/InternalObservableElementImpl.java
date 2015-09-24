package org.observe.collect.impl;

import org.observe.DefaultObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;

import com.google.common.reflect.TypeToken;

class InternalObservableElementImpl<T> extends DefaultObservableValue<T> {
	private Observer<ObservableValueEvent<T>> theController;

	private final TypeToken<T> theType;
	private T theValue;

	InternalObservableElementImpl(TypeToken<T> type, T value) {
		theType = type;
		theValue = (T) theType.wrap().getRawType().cast(value);
		theController = control(null);
	}

	void set(T newValue) {
		T oldValue = theValue;
		theValue = (T) theType.getRawType().cast(newValue);
		theController.onNext(createChangeEvent(oldValue, newValue, null));
	}

	void remove() {
		ObservableValueEvent<T> event = createChangeEvent(theValue, theValue, null);
		theController.onCompleted(event);
	}

	@Override
	public TypeToken<T> getType() {
		return theType;
	}

	@Override
	public T get() {
		return theValue;
	}

	@Override
	public String toString() {
		return String.valueOf(theValue);
	}
}
