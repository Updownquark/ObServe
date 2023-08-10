package org.observe.expresso;

import org.qommons.config.QonfigException;
import org.qommons.io.LocatedFilePosition;

/**
 * Thrown from {@link ObservableModelSet.InterpretedValueSynth#get(ObservableModelSet.ModelSetInstance)} if an instance of the model value
 * could not be created
 */
public class ModelInstantiationException extends QonfigException {
	/**
	 * @param message A message describing the failure
	 * @param position The position of the error within the Qonfig element defining the model value
	 * @param errorLength The length of the character sequence where the error occurred
	 */
	public ModelInstantiationException(String message, LocatedFilePosition position, int errorLength) {
		super(message, position, errorLength);
	}

	/**
	 * @param message A message describing the failure
	 * @param position The position of the error within the Qonfig element defining the model value
	 * @param errorLength The length of the character sequence where the error occurred
	 * @param cause The cause of this exception
	 */
	public ModelInstantiationException(String message, LocatedFilePosition position, int errorLength, Throwable cause) {
		super(message, position, errorLength, cause);
	}

	/**
	 * @param position The position of the error within the Qonfig element defining the model value
	 * @param errorLength The length of the character sequence where the error occurred
	 * @param cause The cause of this exception
	 */
	public ModelInstantiationException(LocatedFilePosition position, int errorLength, Throwable cause) {
		super(position, errorLength, cause);
	}
}
