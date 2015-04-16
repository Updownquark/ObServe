package org.observe.util;

import org.observe.Observable;
import org.observe.Observer;

/**
 * Wraps an observable
 * 
 * @param <T> The type of the observable
 */
public class ObservableWrapper<T> implements Observable<T> {
	private final Observable<T> theWrapped;

	/** @param wrap The observable to wrap */
	public ObservableWrapper(Observable<T> wrap) {
		theWrapped = wrap;
	}

	@Override
	public Runnable observe(Observer<? super T> observer) {
		return theWrapped.observe(observer);
	}

	@Override
	public String toString() {
		return theWrapped.toString();
	}
}
