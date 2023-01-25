package org.observe.expresso;

import java.text.ParseException;

/** An error that occurs in parsing or evaluating {@link ObservableExpression}s */
public abstract class ExpressoException extends ParseException {
	private final int theEndIndex;

	/**
	 * @param errorOffset The start index of the error
	 * @param endIndex The end index of the error
	 * @param message The message for the exception
	 */
	public ExpressoException(int errorOffset, int endIndex, String message) {
		super(message + " at position " + errorOffset, errorOffset);
		theEndIndex = endIndex;
	}

	/**
	 * @param errorOffset The start index of the error
	 * @param endIndex The end index of the error
	 * @param message The message for the exception
	 * @param cause The cause of the exception
	 */
	public ExpressoException(int errorOffset, int endIndex, String message, Throwable cause) {
		super(message + " at position " + errorOffset, errorOffset);
		theEndIndex = endIndex;
	}

	/**
	 * @param exp The expression
	 * @param message The message for the exception
	 */
	public ExpressoException(Expression exp, String message) {
		this(exp.getStartIndex(), exp.getEndIndex(), message);
	}

	/** @return The start index of the error */
	@Override
	public int getErrorOffset() {
		return super.getErrorOffset();
	}

	/** @return The end index of the error */
	public int getEndIndex() {
		return theEndIndex;
	}
}
