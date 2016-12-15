package org.observe.util;

import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;

import com.google.common.reflect.TypeToken;

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
	public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
		return theWrapped.subscribe(observer);
	}

	@Override
	public TypeToken<T> getType() {
		return theWrapped.getType();
	}

	@Override
	public T get() {
		return theWrapped.get();
	}

	@Override
	public boolean isSafe() {
		return theWrapped.isSafe();
	}

	@Override
	public String toString() {
		return theWrapped.toString();
	}
}
