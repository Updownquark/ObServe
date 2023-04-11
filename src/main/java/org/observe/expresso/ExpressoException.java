package org.observe.expresso;

import java.text.ParseException;

/** An error that occurs in parsing or evaluating {@link ObservableExpression}s */
public abstract class ExpressoException extends ParseException {
	private final int theLength;

	/**
	 * @param errorOffset The start index of the error
	 * @param length The text length of the error
	 * @param message The message for the exception
	 */
	public ExpressoException(int errorOffset, int length, String message) {
		super(message + " at position " + errorOffset, errorOffset);
		theLength = length;
	}

	/**
	 * @param errorOffset The start index of the error
	 * @param length The text length of the error
	 * @param message The message for the exception
	 * @param cause The cause of the exception
	 */
	public ExpressoException(int errorOffset, int length, String message, Throwable cause) {
		super(message + " at position " + errorOffset, errorOffset);
		initCause(cause);
		theLength = length;
	}

	/**
	 * @param exp The expression
	 * @param message The message for the exception
	 */
	public ExpressoException(Expression exp, String message) {
		this(exp.getStartIndex(), exp.getEndIndex(), message);
	}

	/**
	 * @param exp The expression
	 * @param message The message for the exception
	 * @param cause The cause of the exception
	 */
	public ExpressoException(Expression exp, String message, Throwable cause) {
		this(exp.getStartIndex(), exp.getEndIndex(), message, cause);
	}

	/** @return The start index of the error */
	@Override
	public int getErrorOffset() {
		return super.getErrorOffset();
	}

	/** @return The text length of the error */
	public int getErrorLength() {
		return theLength;
	}
}
