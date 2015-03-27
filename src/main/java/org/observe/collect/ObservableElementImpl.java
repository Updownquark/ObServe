package org.observe.collect;

import org.observe.DefaultObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;

import prisms.lang.Type;

class ObservableElementImpl<T> extends DefaultObservableValue<T> {
	private Observer<ObservableValueEvent<T>> theController;
	private final Type theType;
	private T theValue;

	ObservableElementImpl(Type type, T value) {
		theType = type;
		theValue = value;
		theController = control(null);
	}

	void set(T newValue) {
		T oldValue = theValue;
		theValue = newValue;
		theController.onNext(new ObservableValueEvent<>(this, oldValue, newValue, null));
	}

	void remove() {
		theController.onCompleted(new ObservableValueEvent<>(this, theValue, theValue, null));
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