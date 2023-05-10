package org.observe.expresso;

import org.qommons.io.FilePosition;
import org.qommons.io.TextParseException;

public class ExpressoRuntimeException extends RuntimeException {
	private final FilePosition thePosition;

	/**
	 * @param s The message for the exception
	 * @param errorOffset The character offset of the error in the sequence
	 * @param lineNumber The line number of the error in the sequence, offset from zero
	 * @param columnNumber The character number of the error in the line, offset from zero
	 */
	public ExpressoRuntimeException(String s, int errorOffset, int lineNumber, int columnNumber) {
		super(s);
		thePosition = new FilePosition(errorOffset, lineNumber, columnNumber);
	}

	/**
	 * @param s The message for the exception
	 * @param errorOffset The character offset of the error in the sequence
	 * @param lineNumber The line number of the error in the sequence, offset from zero
	 * @param columnNumber The character number of the error in the line, offset from zero
	 * @param cause The cause of the exception
	 */
	public ExpressoRuntimeException(String s, int errorOffset, int lineNumber, int columnNumber, Throwable cause) {
		super(s);
		initCause(cause);
		thePosition = new FilePosition(errorOffset, lineNumber, columnNumber);
	}

	/**
	 * @param s The message for the exception
	 * @param position The position in the sequence
	 */
	public ExpressoRuntimeException(String s, FilePosition position) {
		super(s);
		thePosition = position;
	}

	/**
	 * @param s The message for the exception
	 * @param position The position in the sequence
	 * @param cause The cause of the exception
	 */
	public ExpressoRuntimeException(String s, FilePosition position, Throwable cause) {
		super(s);
		initCause(cause);
		thePosition = position;
	}

	/**
	 * @param s The message for the exception
	 * @param cause The cause of the exception
	 */
	public ExpressoRuntimeException(String s, TextParseException cause) {
		super(s);
		initCause(cause);
		thePosition = cause.getPosition();
	}

	/** @return The position of the source of the error in the file */
	public FilePosition getPosition() {
		return thePosition;
	}

	/** @return The line number of the error in the sequence, offset from zero */
	public int getLineNumber() {
		return thePosition.getLineNumber();
	}

	/** @return The character number of the error in the line, offset from zero */
	public int getColumnNumber() {
		return thePosition.getCharNumber();
	}

	@Override
	public String toString() {
		if (thePosition != null)
			return new StringBuilder().append(thePosition).append(": ").append(super.toString()).toString();
		else
			return super.toString();
	}

}
