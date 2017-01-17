package org.observe;

import org.qommons.Causable;

/**
 * Listens to an observable
 *
 * @param <T> The super type of observable that this observer may listen to
 */
@FunctionalInterface
public interface Observer<T> {
	/**
	 * @param <V> The actual type of the value
	 * @param value The latest value on the observable
	 */
	<V extends T> void onNext(V value);

	/**
	 * Signals that the observable has no more values
	 *
	 * @param <V> The actual type of the value
	 * @param value The final value, or null if not applicable
	 */
	default <V extends T> void onCompleted(V value) {
	}

	/** @param e The error that occurred in the observable */
	default void onError(Throwable e) {
		if (e instanceof ObservableErrorException)
			throw (ObservableErrorException) e;
		throw new ObservableErrorException(e);
	}

	/**
	 * Calls an observer and then calls {@link Causable#finish()} on the value
	 * 
	 * @param observer The observer to call {@link #onNext(Object)} with the value
	 * @param value The value to call with the the observable
	 */
	static <T extends Causable> void onNextAndFinish(Observer<? super T> observer, T value) {
		observer.onNext(value);
		value.finish();
	}

	/**
	 * Calls an observer and then calls {@link Causable#finish()} on the value
	 * 
	 * @param observer The observer to call {@link #onCompleted(Object)} with the value
	 * @param value The value to call with the the observable
	 */
	static <T extends Causable> void onCompletedAndFinish(Observer<? super T> observer, T value) {
		observer.onCompleted(value);
		value.finish();
	}
}
