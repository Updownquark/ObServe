package org.observe;

import org.qommons.Causable;

/**
 * Listens to an observable
 *
 * @param <T> The super type of observable that this observer may listen to
 */
public interface Observer<T> {
	/**
	 * @param <V> The actual type of the value
	 * @param value The latest value on the observable
	 */
	<V extends T> void onNext(V value);

	/**
	 * Signals that the observable has no more values
	 *
	 * @param cause The cause of the completion
	 */
	void onCompleted(Causable cause);
}
