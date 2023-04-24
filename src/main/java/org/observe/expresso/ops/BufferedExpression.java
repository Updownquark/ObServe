package org.observe.expresso.ops;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;

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
	public List<? extends ObservableExpression> getChildren() {
		return Arrays.asList(theExpression);
	}

	@Override
	public int getChildOffset(int childIndex) {
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
	public ModelType<?> getModelType(ExpressoEnv env) {
		return theExpression.getModelType(env);
	}

	@Override
	public <M, MV extends M> ModelValueSynth<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env, int expressionOffset)
		throws ExpressoEvaluationException, ExpressoInterpretationException {
		return theExpression.evaluateInternal(type, env, expressionOffset + theBefore);
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
