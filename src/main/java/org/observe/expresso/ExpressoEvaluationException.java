package org.observe.expresso;

/**
 * Thrown from {@link ObservableExpression#evaluate(org.observe.expresso.ModelType.ModelInstanceType, ExpressoEnv)} when the expression
 * cannot be evaluated
 */
public class ExpressoEvaluationException extends ExpressoException {
	/** @see ExpressoException#ExpressoException(Expression, String) */
	public ExpressoEvaluationException(Expression exp, String message) {
		super(exp, message);
	}

	/** @see ExpressoException#ExpressoException(int, int, String, Throwable) */
	public ExpressoEvaluationException(int errorOffset, int endIndex, String message, Throwable cause) {
		super(errorOffset, endIndex, message, cause);
	}

	/** @see ExpressoException#ExpressoException(int, int, String) */
	public ExpressoEvaluationException(int errorOffset, int endIndex, String message) {
		super(errorOffset, endIndex, message);
	}
}
