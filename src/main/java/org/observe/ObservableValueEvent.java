package org.observe;

import org.qommons.AbstractCausable;

/**
 * An event representing the change of an observable's value
 *
 * @param <T> The compile-time type of the observable's value
 */
public class ObservableValueEvent<T> extends AbstractCausable {
	private final ObservableValue<T> theObservable;

	private final boolean isInitial;
	private final T theOldValue;
	private final T theNewValue;

	/**
	 * @param observable The observable whose value changed
	 * @param initial Whether this represents the population of the initial value of an observable value in response to subscription
	 * @param oldValue The old value of the observable
	 * @param newValue The new value in the observable
	 * @param cause The cause of this event--typically another event or null
	 */
	protected ObservableValueEvent(ObservableValue<T> observable, boolean initial, T oldValue, T newValue, Object cause) {
		super(cause);
		theObservable = observable;
		isInitial = initial;
		if(oldValue != null) // Allow null for old value even for primitive types
			oldValue = (T) observable.getType().wrap().getRawType().cast(oldValue);
		theOldValue = oldValue;
		theNewValue = (T) observable.getType().wrap().getRawType().cast(newValue);
	}

	/** @return The observable that caused this event */
	public ObservableValue<T> getObservable() {
		return theObservable;
	}

	/** @return Whether this represents the population of the initial value of an observable value in response to subscription */
	public boolean isInitial() {
		return isInitial;
	}

	/** @return The old value of the observable */
	public T getOldValue() {
		return theOldValue;
	}

	/** @return The new value in the observable */
	public T getValue() {
		return theNewValue;
	}

	@Override
	public String toString(){
		return theObservable + ": " + theOldValue + "->" + theNewValue;
	}

	/**
	 * Creates an event to populate the initial value of an observable to a subscriber
	 *
	 * @param <T> The type of the observable value
	 * @param observable The observable value to populate the value for
	 * @param value The current value of the observable
	 * @param cause The cause of the event (typically null for initial events)
	 * @return The event to fire
	 */
	public static <T> ObservableValueEvent<T> createInitialEvent(ObservableValue<T> observable, T value, Object cause) {
		return new ObservableValueEvent<>(observable, true, null, value, cause);
	}

	/**
	 * Creates an event representing an observable's change of value
	 *
	 * @param <T> The type of the observable value
	 * @param observable The observable value to populate the value for
	 * @param oldValue The value of the observable before the change
	 * @param newValue The value of the observable after the change (current)
	 * @param cause The cause of the event (may be null)
	 * @return The event to fire
	 */
	public static <T> ObservableValueEvent<T> createChangeEvent(ObservableValue<T> observable, T oldValue, T newValue, Object cause) {
		return new ObservableValueEvent<>(observable, false, oldValue, newValue, cause);
	}
}
