package org.observe.entity;

/** Thrown from an entity set implementation when a requested operation cannot be performed for any reason */
public class EntityOperationException extends Exception {
	/** @param message A message describing the cause of the failure */
	public EntityOperationException(String message) {
		super(message);
	}

	/** @param cause Another exception that caused this failure */
	public EntityOperationException(Throwable cause) {
		super(cause);
	}

	/**
	 * @param message A message describing the cause of the failure
	 * @param cause Another exception that caused this failure
	 */
	public EntityOperationException(String message, Throwable cause) {
		super(message, cause);
	}
}
