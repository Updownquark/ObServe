package org.observe.config;

/** Thrown from an operation on an observable data set when a requested operation cannot be performed for any reason */
public class ValueOperationException extends Exception {
	/** @param message A message describing the cause of the failure */
	public ValueOperationException(String message) {
		super(message);
	}

	/** @param cause Another exception that caused this failure */
	public ValueOperationException(Throwable cause) {
		super(cause);
	}

	/**
	 * @param message A message describing the cause of the failure
	 * @param cause Another exception that caused this failure
	 */
	public ValueOperationException(String message, Throwable cause) {
		super(message, cause);
	}
}
