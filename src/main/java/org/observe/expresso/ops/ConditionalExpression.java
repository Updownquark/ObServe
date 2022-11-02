package org.observe.expresso.ops;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigInterpretationException;

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
	public List<? extends ObservableExpression> getChildren() {
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
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigInterpretationException {
		if (type != null && (type.getModelType() == ModelTypes.Value || type.getModelType() == ModelTypes.Collection
			|| type.getModelType() == ModelTypes.Set)) {//
		} else
			throw new QonfigInterpretationException(
				"Conditional expressions not supported for model type " + type.getModelType() + " (" + this + ")");
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> conditionV = theCondition.evaluate(//
			ModelTypes.Value.forType(boolean.class), env);
		ValueContainer<M, MV> primaryV = thePrimary.evaluate(type, env);
		ValueContainer<M, MV> secondaryV = theSecondary.evaluate(type, env);
		// TODO reconcile compatible model types, like Collection and Set
		if (primaryV.getType().getModelType() != secondaryV.getType().getModelType())
			throw new QonfigInterpretationException("Incompatible expressions: " + thePrimary + ", evaluated to " + primaryV.getType()
			+ " and " + theSecondary + ", evaluated to " + secondaryV.getType());
		if (primaryV.getType().getModelType() == ModelTypes.Value || primaryV.getType().getModelType() == ModelTypes.Collection
			|| primaryV.getType().getModelType() == ModelTypes.Set) {//
		} else
			throw new QonfigInterpretationException(
				"Conditional expressions not supported for model type " + primaryV.getType().getModelType() + " (" + this + ")");

		ModelInstanceType<M, MV> resultType;
		if (primaryV.getType().equals(secondaryV.getType()))
			resultType = primaryV.getType();
		else if (thePrimary instanceof LiteralExpression && ((LiteralExpression<?>) thePrimary).getValue() == null)
			resultType = secondaryV.getType();
		else if (theSecondary instanceof LiteralExpression && ((LiteralExpression<?>) theSecondary).getValue() == null)
			resultType = primaryV.getType();
		else {
			TypeToken<?>[] types = new TypeToken[primaryV.getType().getModelType().getTypeCount()];
			for (int i = 0; i < types.length; i++)
				types[i] = TypeTokens.get().getCommonType(primaryV.getType().getType(i), secondaryV.getType().getType(i));
			resultType = (ModelInstanceType<M, MV>) primaryV.getType().getModelType().forTypes(types);
		}
		return new ValueContainer<M, MV>() {
			@Override
			public ModelInstanceType<M, MV> getType() {
				return resultType;
			}

			@Override
			public MV get(ModelSetInstance msi) {
				SettableValue<Boolean> conditionX = conditionV.get(msi);
				Object primaryX = primaryV.get(msi);
				Object secondaryX = secondaryV.get(msi);
				return createValue(conditionX, primaryX, secondaryX);
			}

			private MV createValue(SettableValue<Boolean> conditionX, Object primaryX, Object secondaryX) {
				if (primaryV.getType().getModelType() == ModelTypes.Value) {
					return (MV) SettableValue.flattenAsSettable(conditionX.map(
						TypeTokens.get().keyFor(ObservableValue.class).<SettableValue<Object>> parameterized(resultType.getType(0)),
						c -> {
							if (c != null && c)
								return (SettableValue<Object>) primaryX;
							else
								return (SettableValue<Object>) secondaryX;
						}), null);
				} else if (primaryV.getType().getModelType() == ModelTypes.Collection) {
					return (MV) ObservableCollection.flattenValue(conditionX.map(TypeTokens.get().keyFor(ObservableCollection.class)
						.<ObservableCollection<Object>> parameterized(resultType.getType(0)), c -> {
							if (c != null && c)
								return (ObservableCollection<Object>) primaryX;
							else
								return (ObservableCollection<Object>) secondaryX;
						}));
				} else if (primaryV.getType().getModelType() == ModelTypes.Set) {
					return (MV) ObservableSet.flattenValue(conditionX.map(
						TypeTokens.get().keyFor(ObservableSet.class).<ObservableSet<Object>> parameterized(resultType.getType(0)),
						c -> {
							if (c != null && c)
								return (ObservableSet<Object>) primaryX;
							else
								return (ObservableSet<Object>) secondaryX;
						}));
				} else
					throw new IllegalStateException(
						"Conditional expressions not supported for model type " + primaryV.getType().getModelType());
			}

			@Override
			public MV forModelCopy(MV value, ModelSetInstance sourceModels, ModelSetInstance newModels) {
				SettableValue<Boolean> sourceCondition = conditionV.get(sourceModels);
				SettableValue<Boolean> newCondition = conditionV.forModelCopy(sourceCondition, sourceModels, newModels);
				Object sourcePrimary = primaryV.get(sourceModels);
				Object newPrimary = ((ValueContainer<Object, Object>) primaryV).forModelCopy(sourcePrimary, sourceModels, newModels);
				Object sourceSecondary = secondaryV.get(sourceModels);
				Object newSecondary = ((ValueContainer<Object, Object>) secondaryV).forModelCopy(sourceSecondary, sourceModels, newModels);
				if (sourceCondition == newCondition && sourcePrimary == newPrimary && sourceSecondary == newSecondary)
					return value;
				return createValue(newCondition, newPrimary, newSecondary);
			}

			@Override
			public BetterList<ValueContainer<?, ?>> getCores() {
				return BetterList.of(Stream.of(conditionV, primaryV, secondaryV).flatMap(vc -> vc.getCores().stream()));
			}
		};
	}

	@Override
	public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ExpressoEnv env)
		throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not supported for conditionals");
	}

	@Override
	public String toString() {
		return theCondition + " ? " + thePrimary + " : " + theSecondary;
	}
}