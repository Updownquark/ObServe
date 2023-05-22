package org.observe.expresso.ops;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;

/** An expression that returns a boolean for whether a given expression's value is an instance of a constant type */
public class InstanceofExpression implements ObservableExpression {
	private final ObservableExpression theLeft;
	private final BufferedType theType;

	/**
	 * @param left The expression whose type to check
	 * @param type The type to check against
	 */
	public InstanceofExpression(ObservableExpression left, BufferedType type) {
		theLeft = left;
		theType = type;
	}

	/** @return The expression whose type to check */
	public ObservableExpression getLeft() {
		return theLeft;
	}

	/** @return The type to check against */
	public BufferedType getType() {
		return theType;
	}

	@Override
	public int getChildOffset(int childIndex) {
		if (childIndex != 0)
			throw new IndexOutOfBoundsException(childIndex + " of 1");
		return 0;
	}

	@Override
	public int getExpressionLength() {
		return theLeft.getExpressionLength() + 12 + theType.length();
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return Collections.singletonList(theLeft);
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		ObservableExpression left = theLeft.replaceAll(replace);
		if (left != theLeft)
			return new InstanceofExpression(left, theType);
		return this;
	}

	@Override
	public ModelType<?> getModelType(ExpressoEnv env) {
		return ModelTypes.Value;
	}

	@Override
	public <M, MV extends M> ModelValueSynth<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env, int expressionOffset)
		throws ExpressoEvaluationException, ExpressoInterpretationException {
		if (type.getModelType() != ModelTypes.Value && !TypeTokens.get().isAssignable(type.getType(0), TypeTokens.get().BOOLEAN))
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				"instanceof expressions can only be evaluated to Value<Boolean>");
		ModelValueSynth<SettableValue<?>, SettableValue<?>> leftValue;
		try {
			leftValue = theLeft.evaluate(ModelTypes.Value.any(), env, expressionOffset);
		} catch (TypeConversionException e) {
			throw new ExpressoEvaluationException(expressionOffset, theLeft.getExpressionLength(), e.getMessage(), e);
		}
		Class<?> testType;
		try {
			testType = TypeTokens.getRawType(env.getClassView().parseType(theType.getName()));
		} catch (ParseException e) {
			if (e.getErrorOffset() == 0)
				throw new ExpressoEvaluationException(expressionOffset + theLeft.getExpressionLength() + 12, theType.length(),
					e.getMessage(), e);
			else
				throw new ExpressoEvaluationException(expressionOffset + theLeft.getExpressionLength() + 12 + e.getErrorOffset(), 0,
					e.getMessage(), e);
		}
		ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> container = leftValue.map(ModelTypes.Value.forType(boolean.class),
			(lv, msi) -> {
				return SettableValue.asSettable(lv.map(TypeTokens.get().BOOLEAN, v -> v != null && testType.isInstance(v)),
					__ -> "instanceof expressions are not reversible");
			});
		return (ModelValueSynth<M, MV>) container;
	}

	@Override
	public String toString() {
		return theLeft + " instanceof " + theType;
	}
}
