package org.observe.expresso;

import org.qommons.config.QonfigException;
import org.qommons.config.QonfigFilePosition;

public class ModelInstantiationException extends QonfigException {
	public ModelInstantiationException(String message, QonfigFilePosition position, int errorLength) {
		super(message, position, errorLength);
	}

	public ModelInstantiationException(String message, Throwable cause, QonfigFilePosition position, int errorLength) {
		super(message, cause, position, errorLength);
	}

	public ModelInstantiationException(Throwable cause, QonfigFilePosition position, int errorLength) {
		super(cause, position, errorLength);
	}
}
