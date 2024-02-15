package org.observe.expresso.qonfig;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.TypeConversionException;
import org.qommons.ex.ExceptionHandler;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

/** A structure containing an {@link ObservableExpression} and exposes the location in the file where the expression occurred */
public interface LocatedExpression {
	/** @return The observable expression */
	ObservableExpression getExpression();

	/** @return The position in the Qonfig file of the start of the expression */
	LocatedPositionedContent getFilePosition();

	/**
	 * @param offset The offset in the expression of the location to get
	 * @return The location in the file where the portion of this expression at the given offset occurred
	 */
	default LocatedFilePosition getFilePosition(int offset) {
		LocatedPositionedContent pos = getFilePosition();
		return pos == null ? null : pos.getPosition(offset);
	}

	/** @return The length of the expression */
	int length();

	/**
	 * @param <M> The model type to evaluate the expression as
	 * @param <MV> The instance type to evaluate the expression as
	 * @param type The type to evaluate the expression as
	 * @param env The expresso environment to use instead of the one retrieved from the session
	 * @return The evaluated expression
	 * @throws ExpressoInterpretationException If the expression could not be evaluated as the given type
	 */
	default <M, MV extends M> ObservableExpression.EvaluatedExpression<M, MV> interpret(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env) throws ExpressoInterpretationException {
		try {
			return interpret(type, env, ExceptionHandler.thrower2());
		} catch (TypeConversionException e) {
			throw new ExpressoInterpretationException(e.getMessage(), getFilePosition(0), 0, e);
		}
	}

	/**
	 * @param <M> The model type for the value
	 * @param <MV> The instance type for the value
	 * @param <EX> The exception type thrown by the handler in response to non-fatal {@link ExpressoInterpretationException}s
	 * @param <TX> The exception type thrown by the handler in response to {@link TypeConversionException}s
	 * @param type The type to interpret this expression as
	 * @param env The expresso environment to use to interpret the expression
	 * @param exHandler The expression handler to handle non-fatal {@link ExpressoInterpretationException}s and
	 *        {@link TypeConversionException}s
	 * @return The interpreted model value
	 * @throws ExpressoInterpretationException If a fatal exception occurs interpreting the expression
	 * @throws EX If the handler throws it in response to a non-fatal {@link ExpressoInterpretationException}
	 * @throws TX If the handler throws it in response to a {@link TypeConversionException}
	 */
	default <M, MV extends M, EX extends Throwable, TX extends Throwable> ObservableExpression.EvaluatedExpression<M, MV> interpret(
		ModelInstanceType<M, MV> type, InterpretedExpressoEnv env,
		ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, EX, TX> exHandler)
			throws ExpressoInterpretationException, EX, TX {
		return getExpression()//
			.evaluate(//
				type, env.at(getFilePosition()), 0, exHandler);
	}
}
