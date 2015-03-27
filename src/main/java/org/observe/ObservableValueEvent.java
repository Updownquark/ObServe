package org.observe;

/**
 * An event representing the change of an observable's value
 *
 * @param <T> The compile-time type of the observable's value
 */
public class ObservableValueEvent<T> {
	private final ObservableValue<T> theObservable;

	private final T theOldValue;
	private final T theNewValue;
	private final Object theCause;

	/**
	 * @param observable The observable whose value changed
	 * @param oldValue The old value of the observable
	 * @param newValue The new value in the observable
	 * @param cause The cause of this event--typically another event or null
	 */
	public ObservableValueEvent(ObservableValue<T> observable, T oldValue, T newValue, Object cause) {
		theObservable = observable;
		if(oldValue != null) // Allow null for old value even for primitive types
			oldValue = (T) observable.getType().cast(oldValue);
		theOldValue = oldValue;
		theNewValue = (T) observable.getType().cast(newValue);
		theCause = cause;
	}

	/** @return The observable that caused this event */
	public ObservableValue<T> getObservable() {
		return theObservable;
	}

	/** @return The old value of the observable */
	public T getOldValue() {
		return theOldValue;
	}

	/** @return The new value in the observable */
	public T getValue() {
		return theNewValue;
	}

	/** @return The cause of this event--typically another event or null */
	public Object getCause() {
		return theCause;
	}

	@Override
	public String toString(){
		return theObservable + ": " + theOldValue + "->" + theNewValue;
	}
}
