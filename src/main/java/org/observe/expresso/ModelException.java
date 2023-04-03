package org.observe.expresso;

/** Thrown from {@link ObservableModelSet#getComponent(String)} if the requested component does not exist in the model */
public class ModelException extends Exception {
	/** @param message A message describing the component that was unavailable */
	public ModelException(String message) {
		super(message);
	}

	/**
	 * @param message A message describing the component that was unavailable
	 * @param cause The cause of this exception
	 */
	public ModelException(String message, Throwable cause) {
		super(message, cause);
	}
}
