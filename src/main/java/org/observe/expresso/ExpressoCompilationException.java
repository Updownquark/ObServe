package org.observe.expresso;

import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.TextParseException;

/** Thrown from {@link CompiledModelValue#getModelType(CompiledExpressoEnv)} if the model type cannot be evaluated */
public class ExpressoCompilationException extends TextParseException {
	private final int theLength;

	/**
	 * @param message The message for the exception
	 * @param position The file position of the start of the sequence where the error occurred
	 * @param length The length of the sequence where the error occurred
	 */
	public ExpressoCompilationException(String message, LocatedFilePosition position, int length) {
		super(message, position);
		theLength = length;
	}

	/**
	 * @param message The message for the exception
	 * @param fileLocation The location of the file containing the expression where the error occurred
	 * @param errorOffset The offset in the file of the sequence where the error occurred
	 * @param lineNumber The line number in the file of the sequence where the error occurred
	 * @param columnNumber The position in the line of the sequence where the error occurred
	 * @param length The length of the sequence where the error occurred
	 */
	public ExpressoCompilationException(String message, String fileLocation, int errorOffset, int lineNumber, int columnNumber,
		int length) {
		super(message, new LocatedFilePosition(fileLocation, errorOffset, lineNumber, columnNumber));
		theLength = length;
	}

	/**
	 * @param message The message for the exception
	 * @param position The file position of the start of the sequence where the error occurred
	 * @param length The length of the sequence where the error occurred
	 * @param cause The cause of this exception
	 */
	public ExpressoCompilationException(String message, LocatedFilePosition position, int length, Throwable cause) {
		super(message, position, cause);
		theLength = length;
	}

	/**
	 * @param message The message for the exception
	 * @param fileLocation The location of the file containing the expression where the error occurred
	 * @param errorOffset The offset in the file of the sequence where the error occurred
	 * @param lineNumber The line number in the file of the sequence where the error occurred
	 * @param columnNumber The position in the line of the sequence where the error occurred
	 * @param length The length of the sequence where the error occurred
	 * @param cause The cause of this exception
	 */
	public ExpressoCompilationException(String message, String fileLocation, int errorOffset, int lineNumber, int columnNumber, int length,
		Throwable cause) {
		super(message, new LocatedFilePosition(fileLocation, errorOffset, lineNumber, columnNumber), cause);
		theLength = length;
	}

	/** @return The length of the sequence where the error occurred */
	public int getErrorLength() {
		return theLength;
	}

	@Override
	public LocatedFilePosition getPosition() {
		return (LocatedFilePosition) super.getPosition();
	}
}
