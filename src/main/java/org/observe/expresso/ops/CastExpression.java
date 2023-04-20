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

import com.google.common.reflect.TypeToken;

/** An expression intended to produce an equivalent value of a different type from a source expression */
public class CastExpression implements ObservableExpression {
	private final ObservableExpression theValue;
	private final BufferedType theType;

	/**
	 * @param value The expression being cast
	 * @param type The type to cast to
	 */
	public CastExpression(ObservableExpression value, BufferedType type) {
		theValue = value;
		theType = type;
	}

	/** @return The expression being cast */
	public ObservableExpression getValue() {
		return theValue;
	}

	/** @return The type to cast to */
	public BufferedType getType() {
		return theType;
	}

	@Override
	public int getChildOffset(int childIndex) {
		if (childIndex != 0)
			throw new IndexOutOfBoundsException(childIndex + " of 1");
		return theType.length() + 2;
	}

	@Override
	public int getExpressionLength() {
		return theValue.getExpressionLength() + theType.length() + 2;
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return Collections.singletonList(theValue);
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		ObservableExpression value = theValue.replaceAll(replace);
		if (value != theValue)
			return new CastExpression(value, theType);
		return this;
	}

	@Override
	public ModelType<?> getModelType(ExpressoEnv env) {
		return ModelTypes.Value;
	}

	@Override
	public <M, MV extends M> ModelValueSynth<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env, int expressionOffset)
		throws ExpressoEvaluationException, ExpressoInterpretationException {
		if (type.getModelType() != ModelTypes.Value)
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				"A cast expression can only be evaluated as a value");
		return (ModelValueSynth<M, MV>) doEval((ModelInstanceType<SettableValue<?>, SettableValue<?>>) type, env, expressionOffset);
	}

	private <T> ModelValueSynth<SettableValue<?>, SettableValue<T>> doEval(ModelInstanceType<SettableValue<?>, SettableValue<?>> type,
		ExpressoEnv env, int expressionOffset) throws ExpressoEvaluationException, ExpressoInterpretationException {
		TypeToken<T> valueType;
		try {
			valueType = (TypeToken<T>) TypeTokens.get().parseType(theType.getName());
		} catch (ParseException e) {
			throw new ExpressoEvaluationException(expressionOffset + 1, theType.length(), e.getMessage(), e);
		}
		if (!TypeTokens.get().isAssignable(type.getType(0), valueType))
			throw new ExpressoEvaluationException(expressionOffset + 1, theType.length(),
				"Cannot assign " + valueType + " to " + type.getType(0));
		int valueOffset = expressionOffset + theType.length() + 2;
		ModelValueSynth<SettableValue<?>, SettableValue<T>> valueContainer;
		try {
			valueContainer = theValue.evaluate(ModelTypes.Value.forType(valueType), env, valueOffset);
		} catch (TypeConversionException e) {
			throw new ExpressoEvaluationException(valueOffset, theValue.getExpressionLength(), e.getMessage(), e);
		}
		return valueContainer;
	}

	@Override
	public String toString() {
		return "(" + theType + ")" + theValue;
	}
}
