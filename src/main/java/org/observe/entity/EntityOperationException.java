package org.observe.entity;

public class EntityOperationException extends Exception {
	public EntityOperationException() {
		super();
	}

	public EntityOperationException(String message, Throwable cause) {
		super(message, cause);
	}

	public EntityOperationException(String message) {
		super(message);
	}

	public EntityOperationException(Throwable cause) {
		super(cause);
	}
}
