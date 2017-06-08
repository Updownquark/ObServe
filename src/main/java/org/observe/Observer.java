package org.observe;

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
	 * @param <V> The actual type of the value
	 * @param value The final value, or null if not applicable
	 */
	<V extends T> void onCompleted(V value);
}
