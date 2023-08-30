package org.observe.expresso;

import java.text.ParseException;

/** Thrown from {@link ExpressoParser#parse(String)} in response to text parse or semantic errors */
public class ExpressoParseException extends ParseException {
	private final int theLength;
	private final String theType;

	/**
	 * @param errorOffset The start index of the error
	 * @param endIndex The end index of the error
	 * @param type The token type of the error
	 * @param message The message for the exception
	 */
	public ExpressoParseException(int errorOffset, int endIndex, String type, String message) {
		super(message, errorOffset);
		theLength = endIndex - errorOffset;
		theType = type;
	}

	/**
	 * @param errorOffset The start index of the error
	 * @param endIndex The end index of the error
	 * @param type The token type of the error
	 * @param message The message for the exception
	 * @param cause The cause of the exception
	 */
	public ExpressoParseException(int errorOffset, int endIndex, String type, String message, Throwable cause) {
		super(message, errorOffset);
		initCause(cause);
		theLength = endIndex - errorOffset;
		theType = type;
	}

	/**
	 * @param exp The expression
	 * @param message The message for the exception
	 */
	public ExpressoParseException(Expression exp, String message) {
		this(exp.getStartIndex(), exp.getEndIndex(), exp.getType(), message);
	}

	/**
	 * @param exp The expression
	 * @param message The message for the exception
	 * @param cause The cause of the exception
	 */
	public ExpressoParseException(Expression exp, String message, Throwable cause) {
		this(exp.getStartIndex(), exp.getEndIndex(), exp.getType(), message, cause);
	}

	/** @return The text length of the error */
	public int getErrorLength() {
		return theLength;
	}

	/** @return The token type of the error */
	public String getType() {
		return theType;
	}
}
