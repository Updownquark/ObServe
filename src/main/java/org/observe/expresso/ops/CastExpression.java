package org.observe.expresso.ops;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
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
	public int getComponentOffset(int childIndex) {
		if (childIndex != 0)
			throw new IndexOutOfBoundsException(childIndex + " of 1");
		return theType.length() + 2;
	}

	@Override
	public int getExpressionLength() {
		return theValue.getExpressionLength() + theType.length() + 2;
	}

	@Override
	public List<? extends ObservableExpression> getComponents() {
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
	public ModelType<?> getModelType(CompiledExpressoEnv env) {
		return ModelTypes.Value;
	}

	@Override
	public <M, MV extends M> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, InterpretedExpressoEnv env,
		int expressionOffset) throws ExpressoEvaluationException, ExpressoInterpretationException {
		if (type.getModelType() != ModelTypes.Value)
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				"A cast expression can only be evaluated as a value");
		return (EvaluatedExpression<M, MV>) doEval((ModelInstanceType<SettableValue<?>, SettableValue<?>>) type, env, expressionOffset);
	}

	private <T> EvaluatedExpression<SettableValue<?>, SettableValue<T>> doEval(ModelInstanceType<SettableValue<?>, SettableValue<?>> type,
		InterpretedExpressoEnv env, int expressionOffset) throws ExpressoEvaluationException, ExpressoInterpretationException {
		TypeToken<T> valueType;
		try {
			valueType = (TypeToken<T>) env.getClassView().parseType(theType.getName());
		} catch (ParseException e) {
			throw new ExpressoEvaluationException(expressionOffset + 1, theType.length(), e.getMessage(), e);
		}
		if (!TypeTokens.get().isAssignable(type.getType(0), valueType))
			throw new ExpressoEvaluationException(expressionOffset + 1, theType.length(),
				"Cannot assign " + valueType + " to " + type.getType(0));
		int valueOffset = expressionOffset + theType.length() + 2;
		try {
			// First, see if we can evaluate the expression as the cast type.
			// This can work around some issues such as where flattening is needed, and if it succeeds it's simpler and less troublesome
			return ObservableExpression
				.wrap(theValue.evaluate(ModelTypes.Value.forType(valueType), env.at(theType.length() + 2), valueOffset));
		} catch (ExpressoEvaluationException | ExpressoInterpretationException | TypeConversionException e) {
			// If the result can't be evaluated as the given type, evaluate it as any value and cast it dynamically
			try {
				return ObservableExpression.wrap(evalAsDynamicCast(
					theValue.evaluate(ModelTypes.Value.anyAsV(), env.at(theType.length() + 2), valueOffset), valueType, valueOffset));
			} catch (TypeConversionException e2) {
				throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(), "'" + theValue + "' is not a scalar value",
					e2);
			}
		}
	}

	private <S, T> EvaluatedExpression<SettableValue<?>, SettableValue<T>> evalAsDynamicCast(
		EvaluatedExpression<SettableValue<?>, SettableValue<S>> valueContainer, TypeToken<T> valueType, int expressionOffset)
			throws ExpressoEvaluationException {
		TypeToken<S> sourceType = (TypeToken<S>) valueContainer.getType().getType(0);
		if (!TypeTokens.get().isAssignable(sourceType, valueType)//
			&& !TypeTokens.get().isAssignable(valueType, sourceType))
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				"Cannot cast value of type " + sourceType + " to " + valueType);
		TypeTokens.TypeConverter<S, T> converter;
		try {
			converter = TypeTokens.get().getCast(sourceType, valueType, true, false);
			TypeTokens.TypeConverter<T, S> reverse;
			try {
				reverse = TypeTokens.get().getCast(valueType, sourceType, true, false);
				return ObservableExpression
					.evEx(valueContainer.map(ModelTypes.Value.forType(valueType), vc -> vc.transformReversible(valueType, tx -> tx//
						.map(converter).withReverse(reverse))), valueType, valueContainer);
			} catch (IllegalArgumentException e) {
				String reverseError = "Cannot convert from " + valueType + " to " + sourceType;
				return ObservableExpression.evEx(
					valueContainer.map(ModelTypes.Value.forType(valueType), vc -> SettableValue.asSettable(vc.transform(valueType, tx -> tx//
						.map(converter)), __ -> reverseError)),
					valueContainer);
			}
		} catch (IllegalArgumentException e) {
		}
		Class<T> valueClass = TypeTokens.get().wrap(TypeTokens.getRawType(valueType));
		Class<S> sourceClass = TypeTokens.getRawType(sourceType);
		return ObservableExpression
			.evEx(valueContainer.map(ModelTypes.Value.forType(valueType), vc -> vc.transformReversible(valueType, tx -> tx//
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
				})))), valueContainer);
	}

	@Override
	public String toString() {
		return "(" + theType + ")" + theValue;
	}
}
