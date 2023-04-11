package org.observe.expresso.ops;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ValueContainer;
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
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env, int expressionOffset)
		throws ExpressoEvaluationException, ExpressoInterpretationException {
		if (type.getModelType() != ModelTypes.Value)
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				"A cast expression can only be evaluated as a value");
		return (ValueContainer<M, MV>) doEval((ModelInstanceType<SettableValue<?>, SettableValue<?>>) type, env, expressionOffset);
	}

	private <S, T> ValueContainer<SettableValue<?>, SettableValue<T>> doEval(ModelInstanceType<SettableValue<?>, SettableValue<?>> type,
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
		ValueContainer<SettableValue<?>, SettableValue<S>> valueContainer;
		try {
			valueContainer = (ValueContainer<SettableValue<?>, SettableValue<S>>) (ValueContainer<?, ?>) theValue
				.evaluate(ModelTypes.Value.any(), env, valueOffset);
		} catch (TypeConversionException e) {
			throw new ExpressoEvaluationException(valueOffset, theValue.getExpressionLength(), e.getMessage(), e);
		}
		TypeToken<S> sourceType = (TypeToken<S>) valueContainer.getType().getType(0);
		if (!TypeTokens.get().isAssignable(sourceType, valueType)//
			&& !TypeTokens.get().isAssignable(valueType, sourceType))
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				"Cannot cast value of type " + sourceType + " to " + valueType);
		Class<T> valueClass = TypeTokens.get().wrap(TypeTokens.getRawType(valueType));
		Class<S> sourceClass = TypeTokens.getRawType(sourceType);
		return valueContainer.map(ModelTypes.Value.forType(valueType), vc -> vc.transformReversible(valueType, tx -> tx//
			.map(v -> {
				if (v == null || valueClass.isInstance(v))
					return (T) v;
				else {
					System.err.println("Cast failed: " + v + " (" + v.getClass().getName() + ") to " + valueType);
					return null;
				}
			}).replaceSource(v -> (S) v, replace -> replace.rejectWith(v -> {
				if (v != null && !sourceClass.isInstance(v))
					return theValue + " (" + v.getClass().getName() + ") is not an instance of " + sourceType;
				return null;
			}))));
	}

	@Override
	public String toString() {
		return "(" + theType + ")" + theValue;
	}
}
