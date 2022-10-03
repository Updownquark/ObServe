package org.observe.expresso.ops;

import java.text.ParseException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/** An expression intended to produce an equivalent value of a different type from a source expression */
public class CastExpression implements ObservableExpression {
	private final ObservableExpression theValue;
	private final String theType;

	/**
	 * @param value The expression being cast
	 * @param type The string representing the type to cast to
	 */
	public CastExpression(ObservableExpression value, String type) {
		theValue = value;
		theType = type;
	}

	/** @return The expression being cast */
	public ObservableExpression getValue() {
		return theValue;
	}

	/** @return The string representing the type to cast to */
	public String getType() {
		return theType;
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
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
		if (type.getModelType() != ModelTypes.Value)
			throw new QonfigInterpretationException("A cast expression can only be evaluated as a value");
		return (ValueContainer<M, MV>) doEval((ModelInstanceType<SettableValue<?>, SettableValue<?>>) type, env);
	}

	private <S, T> ValueContainer<SettableValue<?>, SettableValue<T>> doEval(ModelInstanceType<SettableValue<?>, SettableValue<?>> type,
		ExpressoEnv env) throws QonfigInterpretationException {
		TypeToken<T> valueType;
		try {
			valueType = (TypeToken<T>) TypeTokens.get().parseType(theType);
		} catch (ParseException e) {
			throw new QonfigInterpretationException(e.getMessage(), e);
		}
		if (!TypeTokens.get().isAssignable(type.getType(0), valueType))
			throw new QonfigInterpretationException("Cannot assign " + valueType + " to " + type.getType(0));
		ValueContainer<SettableValue<?>, SettableValue<S>> valueContainer = (ValueContainer<SettableValue<?>, SettableValue<S>>) (ValueContainer<?, ?>) theValue
			.evaluate(ModelTypes.Value.any(), env);
		TypeToken<S> sourceType = (TypeToken<S>) valueContainer.getType().getType(0);
		if (!TypeTokens.get().isAssignable(sourceType, valueType)//
			&& !TypeTokens.get().isAssignable(valueType, sourceType))
			throw new QonfigInterpretationException(
				"Cannot cast value of type " + valueContainer.getType().getType(0) + " to " + valueType);
		Class<T> valueClass = TypeTokens.getRawType(valueType);
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
	public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ExpressoEnv env)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not supported for cast expressions");
	}

	@Override
	public String toString() {
		return "(" + theType + ")" + theValue;
	}
}
