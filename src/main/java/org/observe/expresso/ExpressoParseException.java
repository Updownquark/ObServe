package org.observe.expresso;

/** Thrown from {@link ExpressoParser#parse(String)} in response to text parse or semantic errors */
public class ExpressoParseException extends ExpressoException {
	private final String theType;

	/**
	 * @param errorOffset The start index of the error
	 * @param endIndex The end index of the error
	 * @param type The token type of the error
	 * @param message The message for the exception
	 */
	public ExpressoParseException(int errorOffset, int endIndex, String type, String message) {
		super(errorOffset, endIndex, message);
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
		super(errorOffset, endIndex, message, cause);
		theType = type;
	}

	/**
	 * @param exp The expression
	 * @param message The message for the exception
	 */
	public ExpressoParseException(Expression exp, String message) {
		super(exp, message);
		theType = exp.getType();
	}

	/** @return The token type of the error */
	public String getType() {
		return theType;
	}
}