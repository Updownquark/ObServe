package org.observe;

/** Thrown from {@link Observer#onError(Throwable)} by default */
public class ObservableErrorException extends RuntimeException {
	/** @param cause The exception that caused this exception */
	public ObservableErrorException(Throwable cause) {
		super(cause);
	}
}
