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
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.observe.util.TypeTokens.TypeConverter;
import org.qommons.io.ErrorReporting;

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
	public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) {
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
				return ObservableExpression
					.wrap(evalAsDynamicCast(theValue.evaluate(ModelTypes.Value.anyAsV(), env.at(theType.length() + 2), valueOffset),
						valueType, valueOffset, env.reporting().at(1)));
			} catch (TypeConversionException e2) {
				throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(), "'" + theValue + "' is not a scalar value",
					e2);
			}
		}
	}

	private <S, T> EvaluatedExpression<SettableValue<?>, SettableValue<T>> evalAsDynamicCast(
		EvaluatedExpression<SettableValue<?>, SettableValue<S>> valueContainer, TypeToken<T> valueType, int expressionOffset,
		ErrorReporting reporting) throws ExpressoEvaluationException {
		TypeToken<S> sourceType = (TypeToken<S>) valueContainer.getType().getType(0);

		TypeTokens.TypeConverter<? super S, ? extends S, ? super T, ? extends T> converter;
		try {
			converter = TypeTokens.get().getCast(valueType, sourceType, true, false);
			if (converter.isTrivial())
				return (EvaluatedExpression<SettableValue<?>, SettableValue<T>>) (EvaluatedExpression<?, ?>) valueContainer;
			return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
				valueContainer.map(ModelTypes.Value.forType(valueType), vc -> new ConvertedInstantiator<>(vc, converter)), valueType,
				valueContainer);
		} catch (IllegalArgumentException e) {
			if (!TypeTokens.get().isAssignable(sourceType, valueType)//
				&& !TypeTokens.get().isAssignable(valueType, sourceType))
				throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
					"Cannot cast value of type " + sourceType + " to " + valueType);
		}
		Class<S> sourceClass = TypeTokens.getRawType(sourceType);
		return ObservableExpression.evEx(expressionOffset, getExpressionLength(), valueContainer.map(ModelTypes.Value.forType(valueType),
			vc -> new CheckingInstantiator<>(vc, sourceClass, valueType, reporting)), valueContainer);
	}

	@Override
	public String toString() {
		return "(" + theType + ")" + theValue;
	}

	static class ConvertedInstantiator<S, T> implements ModelValueInstantiator<SettableValue<T>> {
		private final ModelValueInstantiator<SettableValue<S>> theValue;
		private final TypeTokens.TypeConverter<? super S, ? extends S, ? super T, ? extends T> theConverter;

		ConvertedInstantiator(ModelValueInstantiator<SettableValue<S>> value,
			TypeConverter<? super S, ? extends S, ? super T, ? extends T> converter) {
			theValue = value;
			theConverter = converter;
		}

		@Override
		public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			SettableValue<S> value = theValue.get(models);
			return transform(value);
		}

		private SettableValue<T> transform(SettableValue<S> value) {
			return value.transformReversible((TypeToken<T>) theConverter.getConvertedType(), tx -> tx//
				.map(theConverter).replaceSource(theConverter::reverse, rev -> rev.rejectWith(theConverter::isReversible)));
		}

		@Override
		public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			SettableValue<S> oldSource = theValue.get(sourceModels);
			SettableValue<S> newSource = theValue.forModelCopy(oldSource, sourceModels, newModels);
			if (oldSource == newSource)
				return value;
			return transform(newSource);
		}
	}

	static class CheckingInstantiator<S, T> implements ModelValueInstantiator<SettableValue<T>> {
		private final ModelValueInstantiator<SettableValue<S>> theValue;
		private final Class<S> theSourceClass;
		private final TypeToken<T> theType;
		private final Class<T> theCastClass;
		private final ErrorReporting theReporting;

		public CheckingInstantiator(ModelValueInstantiator<SettableValue<S>> value, Class<S> sourceClass, TypeToken<T> type,
			ErrorReporting reporting) {
			theValue = value;
			theSourceClass = sourceClass;
			theType = type;
			theCastClass = TypeTokens.getRawType(theType);
			theReporting = reporting;
		}

		@Override
		public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			SettableValue<S> value = theValue.get(models);
			return transform(value);
		}

		private SettableValue<T> transform(SettableValue<S> value) {
			return value.transformReversible(theType, tx -> tx//
				.map(v -> {
					if (v == null || theCastClass.isInstance(v))
						return (T) v;
					else {
						theReporting.error("Cast failed: " + v + " (" + v.getClass().getName() + ") to " + theType);
						return null;
					}
				}).replaceSource(v -> (S) v, replace -> replace.rejectWith(v -> {
					if (v != null && !theSourceClass.isInstance(v))
						return theValue + " (" + v.getClass().getName() + ") is not an instance of " + theType;
					return null;
				})));
		}

		@Override
		public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			SettableValue<S> oldSource = theValue.get(sourceModels);
			SettableValue<S> newSource = theValue.forModelCopy(oldSource, sourceModels, newModels);
			if (oldSource == newSource)
				return value;
			return transform(newSource);
		}
	}
}
