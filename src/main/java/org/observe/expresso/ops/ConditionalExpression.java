package org.observe.expresso.ops;

import java.util.List;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.expresso.TypeConversionException;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;

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
		return theCondition.getExpressionLength() + thePrimary.getExpressionLength() + theSecondary.getExpressionLength() + 1;
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
	public ModelType<?> getModelType(ExpressoEnv env) {
		return thePrimary.getModelType(env).getCommonType(theSecondary.getModelType(env));
	}

	@Override
	public <M, MV extends M> EvaluatedExpression<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env,
		int expressionOffset)
			throws ExpressoEvaluationException, ExpressoInterpretationException {
		if (type.getModelType() == ModelTypes.Value || type.getModelType() == ModelTypes.Collection
			|| type.getModelType() == ModelTypes.Set) {//
		} else
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				"Conditional expressions not supported for model type " + type.getModelType() + " (" + this + ")");
		EvaluatedExpression<SettableValue<?>, SettableValue<Boolean>> conditionV;
		try {
			conditionV = theCondition.evaluate(//
				ModelTypes.Value.forType(boolean.class), env, expressionOffset);
		} catch (TypeConversionException e) {
			throw new ExpressoEvaluationException(expressionOffset, theCondition.getExpressionLength(), e.getMessage(), e);
		}
		int primaryOffset = expressionOffset + theCondition.getExpressionLength() + 1;
		ExpressoEnv primaryEnv = env.at(theCondition.getExpressionLength() + 1);
		EvaluatedExpression<M, MV> primaryV;
		try {
			primaryV = thePrimary.evaluate(type, primaryEnv, primaryOffset);
		} catch (TypeConversionException e) {
			throw new ExpressoEvaluationException(primaryOffset, thePrimary.getExpressionLength(), e.getMessage(), e);
		}
		int secondaryOffset = primaryOffset + thePrimary.getExpressionLength() + 1;
		ExpressoEnv secondaryEnv = primaryEnv.at(thePrimary.getExpressionLength() + 1);
		EvaluatedExpression<M, MV> secondaryV;
		try {
			secondaryV = theSecondary.evaluate(type, secondaryEnv, secondaryOffset);
		} catch (TypeConversionException e) {
			throw new ExpressoEvaluationException(secondaryOffset, theSecondary.getExpressionLength(), e.getMessage(), e);
		}
		// TODO reconcile compatible model types, like Collection and Set
		ModelInstanceType<M, MV> primaryType = primaryV.getType();
		ModelInstanceType<M, MV> secondaryType = secondaryV.getType();
		if (primaryType.getModelType() != secondaryType.getModelType())
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(), "Incompatible expressions: " + thePrimary
				+ ", evaluated to " + primaryType + " and " + theSecondary + ", evaluated to " + secondaryType);
		if (primaryType.getModelType() == ModelTypes.Value || primaryType.getModelType() == ModelTypes.Collection
			|| primaryType.getModelType() == ModelTypes.Set) {//
		} else
			throw new ExpressoEvaluationException(expressionOffset, getExpressionLength(),
				"Conditional expressions not supported for model type " + primaryType.getModelType() + " (" + this + ")");

		ModelInstanceType<M, MV> resultType;
		if (primaryType.equals(secondaryType))
			resultType = primaryType;
		else if (thePrimary instanceof LiteralExpression && ((LiteralExpression<?>) thePrimary).getValue() == null)
			resultType = secondaryType;
		else if (theSecondary instanceof LiteralExpression && ((LiteralExpression<?>) theSecondary).getValue() == null)
			resultType = primaryType;
		else {
			TypeToken<?>[] types = new TypeToken[primaryType.getModelType().getTypeCount()];
			for (int i = 0; i < types.length; i++)
				types[i] = TypeTokens.get().getCommonType(primaryType.getType(i), secondaryType.getType(i));
			resultType = (ModelInstanceType<M, MV>) primaryType.getModelType().forTypes(types);
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
			public MV get(ModelSetInstance msi) throws ModelInstantiationException {
				SettableValue<Boolean> conditionX = conditionV.get(msi);
				Object primaryX = primaryV.get(msi);
				Object secondaryX = secondaryV.get(msi);
				return createValue(conditionX, primaryX, secondaryX);
			}

			private MV createValue(SettableValue<Boolean> conditionX, Object primaryX, Object secondaryX) {
				if (primaryType.getModelType() == ModelTypes.Value) {
					return (MV) SettableValue.flattenAsSettable(conditionX.map(
						TypeTokens.get().keyFor(ObservableValue.class).<SettableValue<Object>> parameterized(resultType.getType(0)),
						LambdaUtils.printableFn(c -> {
							if (c != null && c)
								return (SettableValue<Object>) primaryX;
							else
								return (SettableValue<Object>) secondaryX;
						}, () -> "? " + primaryX + ": " + secondaryX, null)), null);
				} else if (primaryType.getModelType() == ModelTypes.Collection) {
					return (MV) ObservableCollection.flattenValue(conditionX.map(TypeTokens.get().keyFor(ObservableCollection.class)
						.<ObservableCollection<Object>> parameterized(resultType.getType(0)), LambdaUtils.printableFn(c -> {
							if (c != null && c)
								return (ObservableCollection<Object>) primaryX;
							else
								return (ObservableCollection<Object>) secondaryX;
						}, () -> "? " + primaryX + ": " + secondaryX, null)));
				} else if (primaryType.getModelType() == ModelTypes.Set) {
					return (MV) ObservableSet.flattenValue(conditionX.map(
						TypeTokens.get().keyFor(ObservableSet.class).<ObservableSet<Object>> parameterized(resultType.getType(0)),
						LambdaUtils.printableFn(c -> {
							if (c != null && c)
								return (ObservableSet<Object>) primaryX;
							else
								return (ObservableSet<Object>) secondaryX;
						}, () -> "? " + primaryX + ": " + secondaryX, null)));
				} else
					throw new IllegalStateException("Conditional expressions not supported for model type " + primaryType.getModelType());
			}

			@Override
			public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<Boolean> sourceCondition = conditionV.get(sourceModels);
				SettableValue<Boolean> newCondition = conditionV.get(newModels);
				Object sourcePrimary = primaryV.get(sourceModels);
				Object newPrimary = ((ModelValueSynth<Object, Object>) primaryV).get(newModels);
				Object sourceSecondary = secondaryV.get(sourceModels);
				Object newSecondary = ((ModelValueSynth<Object, Object>) secondaryV).get(newModels);
				if (sourceCondition == newCondition && sourcePrimary == newPrimary && sourceSecondary == newSecondary)
					return value;
				return createValue(newCondition, newPrimary, newSecondary);
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
			public String toString() {
				return conditionV + " ? " + primaryV + " : " + secondaryV;
			}
		};
	}

	@Override
	public String toString() {
		return theCondition + "?" + thePrimary + ":" + theSecondary;
	}
}