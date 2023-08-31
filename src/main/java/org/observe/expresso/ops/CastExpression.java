package org.observe.expresso.ops;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
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
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;
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
	public <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX {
		if (type.getModelType() != ModelTypes.Value)
			throw new ExpressoInterpretationException("A cast expression can only be evaluated as a value", env.reporting().getPosition(),
				getExpressionLength());
		return (EvaluatedExpression<M, MV>) doEval((ModelInstanceType<SettableValue<?>, SettableValue<?>>) type, env, expressionOffset,
			exHandler);
	}

	private <S, T, EX extends Throwable> EvaluatedExpression<SettableValue<?>, SettableValue<T>> doEval(
		ModelInstanceType<SettableValue<?>, SettableValue<?>> type, InterpretedExpressoEnv env, int expressionOffset,
		ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws ExpressoInterpretationException, EX {
		TypeToken<T> valueType;
		try {
			valueType = (TypeToken<T>) env.getClassView().parseType(theType.getName());
		} catch (ParseException e) {
			throw new ExpressoInterpretationException(e.getMessage(), env.reporting().at(1).getPosition(), theType.length(), e);
		}
		if (!TypeTokens.get().isAssignable(type.getType(0), valueType)) {
			exHandler.handle1(new ExpressoInterpretationException("Cannot assign " + valueType + " to " + type.getType(0),
				env.reporting().getPosition(), getExpressionLength()));
			return null;
		}
		int valueOffset = expressionOffset + theType.length() + 2;

		// First, see if we can evaluate the expression as the cast type.
		// This can work around some issues such as where flattening is needed, and if it succeeds it's simpler and less troublesome
		EvaluatedExpression<SettableValue<?>, SettableValue<T>> evaldX = theValue.evaluate(ModelTypes.Value.forType(valueType),
			env.at(theType.length() + 2), valueOffset, ExceptionHandler.holder2());
		if (evaldX != null)
			return ObservableExpression.wrap(evaldX);
		ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, EX, NeverThrown> doubleX = exHandler
			.stack(ExceptionHandler.holder());
		InterpretedExpressoEnv valueEnv = env.at(theType.length() + 2);
		EvaluatedExpression<SettableValue<?>, SettableValue<S>> evald = theValue.evaluate(ModelTypes.Value.anyAsV(), valueEnv, valueOffset,
			doubleX);
		if (doubleX.get2() != null) {
			exHandler.handle1(new ExpressoInterpretationException(doubleX.get2().getMessage(), valueEnv.reporting().getPosition(),
				theValue.getExpressionLength(), doubleX.get2()));
			return null;
		} else if (evald == null)
			return null;
		return ObservableExpression.wrap(evalAsDynamicCast(evald, valueType, valueOffset, env.reporting().at(1), exHandler));
	}

	private <S, T, EX extends Throwable> EvaluatedExpression<SettableValue<?>, SettableValue<T>> evalAsDynamicCast(
		EvaluatedExpression<SettableValue<?>, SettableValue<S>> valueContainer, TypeToken<T> valueType, int expressionOffset,
		ErrorReporting reporting, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler) throws EX {
		TypeToken<S> sourceType = (TypeToken<S>) valueContainer.getType().getType(0);

		TypeTokens.TypeConverter<? super S, ? extends S, ? super T, ? extends T> converter;
		ExceptionHandler.Single<IllegalArgumentException, NeverThrown> iae = ExceptionHandler.holder();
		converter = TypeTokens.get().getCast(valueType, sourceType, true, false, iae);
		if (converter != null) {
			return ObservableExpression.evEx(expressionOffset, getExpressionLength(),
				valueContainer.map(ModelTypes.Value.forType(valueType), vc -> new ConvertedInstantiator<>(vc, converter)), valueType,
				valueContainer);
		} else if (!TypeTokens.get().isAssignable(sourceType, valueType)//
			&& !TypeTokens.get().isAssignable(valueType, sourceType)) {
			exHandler.handle1(new ExpressoInterpretationException("Cannot cast value of type " + sourceType + " to " + valueType,
				reporting.getPosition(), getExpressionLength() - 1));
			return null;
		} else {
			Class<S> sourceClass = TypeTokens.getRawType(sourceType);
			return ObservableExpression.evEx(expressionOffset, getExpressionLength(), valueContainer
				.map(ModelTypes.Value.forType(valueType), vc -> new CheckingInstantiator<>(vc, sourceClass, valueType, reporting)),
				valueContainer);
		}
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
		public void instantiate() {
			theValue.instantiate();
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
		public void instantiate() {
			theValue.instantiate();
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
