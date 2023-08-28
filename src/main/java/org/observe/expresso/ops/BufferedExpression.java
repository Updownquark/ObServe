package org.observe.expresso.ops;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.TypeConversionException;
import org.qommons.ex.ExceptionHandler;

/** An expression buffered by white space on either side */
public class BufferedExpression implements ObservableExpression {
	private final ObservableExpression theExpression;
	private final int theBefore;
	private final int theAfter;

	/**
	 * @param expression The expression content
	 * @param before The amount of white space before the expression
	 * @param after The amount of white space after the expression
	 */
	public BufferedExpression(ObservableExpression expression, int before, int after) {
		theExpression = expression;
		theBefore = before;
		theAfter = after;
	}

	@Override
	public List<? extends ObservableExpression> getComponents() {
		return Arrays.asList(theExpression);
	}

	@Override
	public int getComponentOffset(int childIndex) {
		if (childIndex != 0)
			throw new IndexOutOfBoundsException(childIndex + " of 1");
		return theBefore;
	}

	@Override
	public int getExpressionLength() {
		return theBefore + theExpression.getExpressionLength() + theAfter;
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		replacement = theExpression.replaceAll(replace);
		if (replacement != theExpression)
			return new BufferedExpression(replacement, theBefore, theAfter);
		return this;
	}

	@Override
	public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset)
		throws ExpressoCompilationException, ExpressoEvaluationException {
		return theExpression.getModelType(env, expressionOffset + theBefore);
	}

	@Override
	public <M, MV extends M, TX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<TypeConversionException, TX> exHandler)
			throws ExpressoEvaluationException, ExpressoInterpretationException, TX {
		return ObservableExpression.wrap(theExpression.evaluateInternal(type, env, expressionOffset + theBefore, exHandler));
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		for (int i = 0; i < theBefore; i++)
			str.append(' ');
		str.append(theExpression);
		for (int i = 0; i < theAfter; i++)
			str.append(' ');
		return str.toString();
	}

	/**
	 * @param before The number of whitespace characters before the expression
	 * @param expression The expression content
	 * @param after The number of whitespace characters after the expression
	 * @return The buffered expression
	 */
	public static ObservableExpression buffer(int before, ObservableExpression expression, int after) {
		if (before == 0 && after == 0)
			return expression;
		return new BufferedExpression(expression, before, after);
	}
}
