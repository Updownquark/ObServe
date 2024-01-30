package org.observe.expresso.ops;

import java.util.List;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
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
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;

import com.google.common.reflect.TypeToken;

/** Represents an operator that evaluates and returns the value of one expression or another depending on a boolean condition */
public class ConditionalExpression implements ObservableExpression {
	private final ObservableExpression theCondition;
	private final ObservableExpression thePrimary;
	private final ObservableExpression theSecondary;

	/**
	 * @param condition The condition to use to determine which expression to evaluate
	 * @param primary The expression to evaluate when the condition is true
	 * @param secondary The expression to evaluate when the condition is false
	 */
	public ConditionalExpression(ObservableExpression condition, ObservableExpression primary, ObservableExpression secondary) {
		theCondition = condition;
		thePrimary = primary;
		theSecondary = secondary;
	}

	/** @return The condition to use to determine which expression to evaluate */
	public ObservableExpression getCondition() {
		return theCondition;
	}

	/** @return The expression to evaluate when the condition is true */
	public ObservableExpression getPrimary() {
		return thePrimary;
	}

	/** @return The expression to evaluate when the condition is false */
	public ObservableExpression getSecondary() {
		return theSecondary;
	}

	@Override
	public int getComponentOffset(int childIndex) {
		switch (childIndex) {
		case 0:
			return 0;
		case 1:
			return theCondition.getExpressionLength() + 1;
		case 2:
			return theCondition.getExpressionLength() + thePrimary.getExpressionLength() + 2;
		default:
			throw new IndexOutOfBoundsException(childIndex + " of 3");
		}
	}

	@Override
	public int getExpressionLength() {
		return theCondition.getExpressionLength() + thePrimary.getExpressionLength() + theSecondary.getExpressionLength() + 2;
	}

	@Override
	public List<? extends ObservableExpression> getComponents() {
		return QommonsUtils.unmodifiableCopy(theCondition, thePrimary, theSecondary);
	}

	@Override
	public ObservableExpression replaceAll(Function<ObservableExpression, ? extends ObservableExpression> replace) {
		ObservableExpression replacement = replace.apply(this);
		if (replacement != this)
			return replacement;
		ObservableExpression condition = theCondition.replaceAll(replace);
		ObservableExpression primary = thePrimary.replaceAll(replace);
		ObservableExpression secondary = theSecondary.replaceAll(replace);
		if (condition != theCondition || primary != thePrimary || secondary != theSecondary)
			return new ConditionalExpression(condition, primary, secondary);
		return this;
	}

	@Override
	public ModelType<?> getModelType(CompiledExpressoEnv env, int expressionOffset) throws ExpressoCompilationException {
		int primaryOffset = expressionOffset + getComponentOffset(1);
		return thePrimary.getModelType(env, primaryOffset).getCommonType(//
			theSecondary.getModelType(env, primaryOffset + thePrimary.getExpressionLength() + 1));
	}

	@Override
	public <M, MV extends M, EX extends Throwable> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
		InterpretedExpressoEnv env, int expressionOffset, ExceptionHandler.Single<ExpressoInterpretationException, EX> exHandler)
			throws ExpressoInterpretationException, EX {
		if (type.getModelType() == ModelTypes.Action || type.getModelType() == ModelTypes.Value
			|| type.getModelType() == ModelTypes.Collection || type.getModelType() == ModelTypes.Set) {//
		} else {
			throw new ExpressoInterpretationException(
				"Conditional expressions not supported for model type " + type.getModelType() + " (" + this + ")",
				env.reporting().getPosition(), getExpressionLength());
		}
		ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, EX, NeverThrown> doubleX = exHandler
			.stack(ExceptionHandler.holder());
		EvaluatedExpression<SettableValue<?>, SettableValue<Boolean>> conditionV = theCondition.evaluate(//
			ModelTypes.Value.forType(boolean.class), env, expressionOffset, doubleX);
		if (doubleX.get2() != null) {
			exHandler.handle1(new ExpressoInterpretationException(doubleX.get2().getMessage(), env.reporting().getPosition(),
				theCondition.getExpressionLength()));
			return null;
		} else if (conditionV == null)
			return null;
		int primaryOffset = expressionOffset + theCondition.getExpressionLength() + 1;
		InterpretedExpressoEnv primaryEnv = env.at(theCondition.getExpressionLength() + 1);
		EvaluatedExpression<M, MV> primaryV = thePrimary.evaluate(type, primaryEnv, primaryOffset, doubleX);
		if (doubleX.get2() != null) {
			exHandler.handle1(new ExpressoInterpretationException(doubleX.get2().getMessage(),
				env.reporting().at(getComponentOffset(1)).getPosition(), thePrimary.getExpressionLength()));
			return null;
		} else if (primaryV == null)
			return null;
		int secondaryOffset = primaryOffset + thePrimary.getExpressionLength() + 1;
		InterpretedExpressoEnv secondaryEnv = primaryEnv.at(thePrimary.getExpressionLength() + 1);
		EvaluatedExpression<M, MV> secondaryV = theSecondary.evaluate(type, secondaryEnv, secondaryOffset, doubleX);
		if (doubleX.get2() != null) {
			exHandler.handle1(new ExpressoInterpretationException(doubleX.get2().getMessage(),
				env.reporting().at(getComponentOffset(2)).getPosition(), theSecondary.getExpressionLength()));
			return null;
		} else if (secondaryV == null)
			return null;

		ModelInstanceType<M, MV> resultType;
		// If either target is literal null, use the type of the other
		if (isLiteralNull(thePrimary))
			resultType = secondaryV.getType();
		else if (isLiteralNull(theSecondary))
			resultType = primaryV.getType();
		else if (primaryV.getType().equals(secondaryV.getType()))
			resultType = primaryV.getType();
		else {
			TypeToken<?>[] types = new TypeToken[primaryV.getType().getModelType().getTypeCount()];
			for (int i = 0; i < types.length; i++)
				types[i] = TypeTokens.get().getCommonType(primaryV.getType().getType(i), secondaryV.getType().getType(i));
			resultType = (ModelInstanceType<M, MV>) primaryV.getType().getModelType().forTypes(types);
		}
		return new EvaluatedExpression<M, MV>() {
			@Override
			public ModelType<M> getModelType() {
				return resultType.getModelType();
			}

			@Override
			public ModelInstanceType<M, MV> getType() {
				return resultType;
			}

			@Override
			public int getExpressionOffset() {
				return expressionOffset;
			}

			@Override
			public int getExpressionLength() {
				return ConditionalExpression.this.getExpressionLength();
			}

			@Override
			public Object getDescriptor() {
				return null;
			}

			@Override
			public List<? extends EvaluatedExpression<?, ?>> getComponents() {
				return QommonsUtils.unmodifiableCopy(conditionV, primaryV, secondaryV);
			}

			@Override
			public ModelValueInstantiator<MV> instantiate() throws ModelInstantiationException {
				return new Instantiator<>(resultType, conditionV.instantiate(), primaryV.instantiate(), secondaryV.instantiate());
			}

			@Override
			public String toString() {
				return conditionV + " ? " + primaryV + " : " + secondaryV;
			}
		};
	}

	private static boolean isLiteralNull(ObservableExpression ex) {
		if (ex instanceof BufferedExpression || ex instanceof ParentheticExpression)
			return isLiteralNull(ex.getComponents().get(0));
		else if (ex instanceof LiteralExpression && ((LiteralExpression<?>) ex).getValue() == null)
			return true;
		else
			return false;
	}

	@Override
	public String toString() {
		return theCondition + "?" + thePrimary + ":" + theSecondary;
	}

	static class Instantiator<M, MV extends M> implements ModelValueInstantiator<MV> {
		private final ModelInstanceType<M, MV> theType;
		private final ModelValueInstantiator<SettableValue<Boolean>> theCondition;
		private final ModelValueInstantiator<MV> thePrimary;
		private final ModelValueInstantiator<MV> theSecondary;

		public Instantiator(ModelInstanceType<M, MV> type, ModelValueInstantiator<SettableValue<Boolean>> condition,
			ModelValueInstantiator<MV> primary, ModelValueInstantiator<MV> secondary) {
			theType = type;
			theCondition = condition;
			thePrimary = primary;
			theSecondary = secondary;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			theCondition.instantiate();
			thePrimary.instantiate();
			theSecondary.instantiate();
		}

		@Override
		public MV get(ModelSetInstance msi) throws ModelInstantiationException {
			SettableValue<Boolean> conditionX = theCondition.get(msi);
			Object primaryX = thePrimary.get(msi);
			Object secondaryX = theSecondary.get(msi);
			return createValue(conditionX, primaryX, secondaryX);
		}

		private MV createValue(SettableValue<Boolean> conditionX, Object primaryX, Object secondaryX) {
			if (theType.getModelType() == ModelTypes.Value) {
				return (MV) SettableValue.flattenAsSettable(
					conditionX.map(TypeTokens.get().keyFor(ObservableValue.class).<SettableValue<Object>> parameterized(theType.getType(0)),
						LambdaUtils.printableFn(c -> {
							if (c != null && c)
								return (SettableValue<Object>) primaryX;
							else
								return (SettableValue<Object>) secondaryX;
						}, () -> "? " + primaryX + ": " + secondaryX, null)),
					null);
			} else if (theType.getModelType() == ModelTypes.Collection) {
				return (MV) ObservableCollection.flattenValue(conditionX.map(
					TypeTokens.get().keyFor(ObservableCollection.class).<ObservableCollection<Object>> parameterized(theType.getType(0)),
					LambdaUtils.printableFn(c -> {
						if (c != null && c)
							return (ObservableCollection<Object>) primaryX;
						else
							return (ObservableCollection<Object>) secondaryX;
					}, () -> "? " + primaryX + ": " + secondaryX, null)));
			} else if (theType.getModelType() == ModelTypes.Set) {
				return (MV) ObservableSet.flattenValue(
					conditionX.map(TypeTokens.get().keyFor(ObservableSet.class).<ObservableSet<Object>> parameterized(theType.getType(0)),
						LambdaUtils.printableFn(c -> {
							if (c != null && c)
								return (ObservableSet<Object>) primaryX;
							else
								return (ObservableSet<Object>) secondaryX;
						}, () -> "? " + primaryX + ": " + secondaryX, null)));
			} else
				throw new IllegalStateException("Conditional expressions not supported for model type " + theType.getModelType());
		}

		@Override
		public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
			SettableValue<Boolean> sourceCondition = theCondition.get(sourceModels);
			SettableValue<Boolean> newCondition = theCondition.forModelCopy(sourceCondition, sourceModels, newModels);
			MV sourcePrimary = thePrimary.get(sourceModels);
			MV newPrimary = thePrimary.forModelCopy(sourcePrimary, sourceModels, newModels);
			MV sourceSecondary = theSecondary.get(sourceModels);
			MV newSecondary = theSecondary.forModelCopy(sourceSecondary, sourceModels, newModels);
			if (sourceCondition == newCondition && sourcePrimary == newPrimary && sourceSecondary == newSecondary)
				return value;
			return createValue(newCondition, newPrimary, newSecondary);
		}

		@Override
		public String toString() {
			return theCondition + "?" + thePrimary + ":" + theSecondary;
		}
	}
}
