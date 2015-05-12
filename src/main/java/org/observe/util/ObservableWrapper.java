package org.observe.util;

import org.observe.Observable;
import org.observe.Observer;
import org.observe.Subscription;

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
	public Subscription subscribe(Observer<? super T> observer) {
		return theWrapped.subscribe(observer);
	}

	@Override
	public String toString() {
		return theWrapped.toString();
	}
}
