package org.observe.util;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;

import prisms.lang.Type;

/**
 * An observable value that wraps another
 *
 * @param <T> The type of the value
 */
public class ObservableValueWrapper<T> implements ObservableValue<T> {
	private final ObservableValue<T> theWrapped;

	/** @param wrap The value to wrap */
	public ObservableValueWrapper(ObservableValue<T> wrap) {
		theWrapped = wrap;
	}

	@Override
	public Runnable internalSubscribe(Observer<? super ObservableValueEvent<T>> observer) {
		return theWrapped.internalSubscribe(observer);
	}

	@Override
	public Type getType() {
		return theWrapped.getType();
	}

	@Override
	public T get() {
		return theWrapped.get();
	}

	@Override
	public String toString() {
		return theWrapped.toString();
	}
}
