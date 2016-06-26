package org.observe;

/** An action with an observable enabled property */
public interface ObservableAction {
	/**
	 * @param cause An object that may have caused the action (e.g. a user event)
	 * @throws IllegalStateException If this action is not enabled
	 */
	void act(Object cause) throws IllegalStateException;

	/** @return An observable whose value reports null if this value can be set directly, or a string describing why it cannot */
	ObservableValue<String> isEnabled();
}
