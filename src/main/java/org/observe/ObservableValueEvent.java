package org.observe;

import org.qommons.AbstractCausable;

import com.google.common.reflect.TypeToken;

/**
 * An event representing the change of an observable's value
 *
 * @param <T> The compile-time type of the observable's value
 */
public class ObservableValueEvent<T> extends AbstractCausable {
	private final boolean isInitial;
	private final T theOldValue;
	private final T theNewValue;

	/**
	 * @param type The type of the observable whose value changed
	 * @param initial Whether this represents the population of the initial value of an observable value in response to subscription
	 * @param oldValue The old value of the observable
	 * @param newValue The new value in the observable
	 * @param cause The cause of this event--typically another event or null
	 */
	protected ObservableValueEvent(TypeToken<T> type, boolean initial, T oldValue, T newValue, Object cause) {
		super(cause);
		isInitial = initial;
		if(oldValue != null) // Allow null for old value even for primitive types
			oldValue = (T) type.wrap().getRawType().cast(oldValue);
		theOldValue = oldValue;
		theNewValue = (T) type.wrap().getRawType().cast(newValue);
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
	public T getNewValue() {
		return theNewValue;
	}

	@Override
	public String toString(){
		return theOldValue + "->" + theNewValue;
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
		return new ObservableValueEvent<>(observable.getType(), true, null, value, cause);
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
		return new ObservableValueEvent<>(observable.getType(), false, oldValue, newValue, cause);
	}
}
