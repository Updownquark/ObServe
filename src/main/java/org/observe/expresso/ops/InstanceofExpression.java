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
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;

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
	public int getComponentOffset(int childIndex) {
		if (childIndex != 0)
			throw new IndexOutOfBoundsException(childIndex + " of 1");
		return 0;
	}

	@Override
	public int getExpressionLength() {
		return theLeft.getExpressionLength() + 12 + theType.length();
	}

	@Override
	public List<? extends ObservableExpression> getComponents() {
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
	public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) {
		return ModelTypes.Value;
	}

	@Override
	public <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX {
		if (type.getModelType() != ModelTypes.Value) {
			throw new ExpressoInterpretationException("instanceof expressions can only be evaluated to Value<Boolean>",
				env.reporting().getPosition(), getExpressionLength());
		} else if (!TypeTokens.get().isAssignable(type.getType(0), TypeTokens.get().BOOLEAN)) {
			exHandler.handle1(new ExpressoInterpretationException("instanceof expressions can only be evaluated to Value<Boolean>",
				env.reporting().getPosition(), getExpressionLength()));
			return null;
		}
		ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, EX, NeverThrown> doubleX = exHandler
			.stack(ExceptionHandler.holder());
		EvaluatedExpression<SettableValue<?>, SettableValue<?>> leftValue = theLeft.evaluate(ModelTypes.Value.any(), env, expressionOffset,
			doubleX);
		if (doubleX.get2() != null) {
			exHandler.handle1(new ExpressoInterpretationException(doubleX.get2().getMessage(), env.reporting().getPosition(),
				theLeft.getExpressionLength(), doubleX.get2()));
			return null;
		} else if (leftValue == null)
			return null;
		Class<?> testType;
		try {
			testType = TypeTokens.getRawType(env.getClassView().parseType(theType.getName()));
		} catch (ParseException e) {
			throw new ExpressoInterpretationException(e.getMessage(),
				env.reporting().at(theLeft.getExpressionLength() + 12 + e.getErrorOffset()).getPosition(),
				e.getErrorOffset() == 0 ? theType.length() : 0, e);
		}
		InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> container = leftValue.map(ModelTypes.Value.forType(boolean.class),
			lv -> new Instantiator<>(lv, testType));
		return ObservableExpression.evEx(expressionOffset, getExpressionLength(), (InterpretedValueSynth<M, MV>) container, testType,
			leftValue);
	}

	@Override
	public String toString() {
		return theLeft + " instanceof " + theType;
	}

	static class Instantiator<S> implements ModelValueInstantiator<SettableValue<Boolean>> {
		private final ModelValueInstantiator<SettableValue<S>> theValue;
		private final Class<?> theTest;

		Instantiator(ModelValueInstantiator<? extends SettableValue<S>> value, Class<?> test) {
			theValue = (ModelValueInstantiator<SettableValue<S>>) value;
			theTest = test;
		}

		@Override
		public void instantiate() {
			theValue.instantiate();
		}

		@Override
		public SettableValue<Boolean> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			return test(theValue.get(models));
		}

		private SettableValue<Boolean> test(SettableValue<?> value) {
			return SettableValue.asSettable(value.map(TypeTokens.get().BOOLEAN, v -> v != null && theTest.isInstance(v)),
				__ -> "instanceof expressions are not reversible");
		}

		@Override
		public SettableValue<Boolean> forModelCopy(SettableValue<Boolean> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
			throws ModelInstantiationException {
			SettableValue<S> oldSource = theValue.get(sourceModels);
			SettableValue<S> newSource = theValue.forModelCopy(oldSource, sourceModels, newModels);
			if (oldSource == newSource)
				return value;
			return test(newSource);
		}
	}
}
