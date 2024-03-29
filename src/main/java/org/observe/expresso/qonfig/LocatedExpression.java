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

	default <M, MV extends M, EX extends Throwable, TX extends Throwable> ObservableExpression.EvaluatedExpression<M, MV> interpret(
		ModelInstanceType<M, MV> type, InterpretedExpressoEnv env,
		ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, EX, TX> exHandler)
		throws ExpressoInterpretationException, EX, TX {
		return getExpression()//
			.evaluate(//
				type, env.at(getFilePosition()), 0, exHandler);
	}
}
