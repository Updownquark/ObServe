package org.observe.expresso;

/**
 * Thrown from {@link ObservableExpression#evaluate(org.observe.expresso.ModelType.ModelInstanceType, InterpretedExpressoEnv, int)} when the expression
 * cannot be evaluated
 */
public class ExpressoEvaluationException extends ExpressoException {
	/** @see ExpressoException#ExpressoException(int, int, String, Throwable) */
	public ExpressoEvaluationException(int errorOffset, int length, String message, Throwable cause) {
		super(errorOffset, length, message, cause);
	}

	/** @see ExpressoException#ExpressoException(int, int, String) */
	public ExpressoEvaluationException(int errorOffset, int length, String message) {
		super(errorOffset, length, message);
	}
}
