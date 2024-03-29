package org.observe;

import java.util.Collection;

import org.qommons.Causable;

/**
 * An event representing the change of an observable's value
 *
 * @param <T> The compile-time type of the observable's value
 */
public class ObservableValueEvent<T> extends Causable.AbstractCausable implements ValueChangeEvent<T> {
	private final boolean isInitial;
	private final T theOldValue;
	private final T theNewValue;

	/**
	 * @param initial Whether this represents the population of the initial value of an observable value in response to subscription
	 * @param oldValue The old value of the observable
	 * @param newValue The new value in the observable
	 * @param causes The causes of this event--typically other events
	 */
	protected ObservableValueEvent(boolean initial, T oldValue, T newValue, Object... causes) {
		super(causes);
		isInitial = initial;
		theOldValue = oldValue;
		theNewValue = newValue;
	}

	/**
	 * @param initial Whether this represents the population of the initial value of an observable value in response to subscription
	 * @param oldValue The old value of the observable
	 * @param newValue The new value in the observable
	 * @param causes The causes of this event--typically other events
	 */
	protected ObservableValueEvent(boolean initial, T oldValue, T newValue, Collection<?> causes) {
		this(initial, oldValue, newValue, causes.toArray());
	}

	@Override
	public boolean isInitial() {
		return isInitial;
	}

	@Override
	public T getOldValue() {
		return theOldValue;
	}

	@Override
	public T getNewValue() {
		return theNewValue;
	}

	/** @return Whether this event represents an update, i.e. an event that's fired even though the value reference hasn't changed */
	public boolean isUpdate() {
		if (isInitial)
			return false;
		else
			return theOldValue == theNewValue;
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
	 * @param causes The cause of the event (typically empty for initial events)
	 * @return The event to fire
	 */
	public static <T> ObservableValueEvent<T> createInitialEvent(ObservableValue<T> observable, T value, Object... causes) {
		return new ObservableValueEvent<>(true, null, value, causes);
	}

	/**
	 * Creates an event representing an observable's change of value
	 *
	 * @param <T> The type of the observable value
	 * @param observable The observable value to populate the value for
	 * @param oldValue The value of the observable before the change
	 * @param newValue The value of the observable after the change (current)
	 * @param causes The causes of the event (may be empty)
	 * @return The event to fire
	 */
	public static <T> ObservableValueEvent<T> createChangeEvent(ObservableValue<T> observable, T oldValue, T newValue, Object... causes) {
		return new ObservableValueEvent<>(false, oldValue, newValue, causes);
	}
}
