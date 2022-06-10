package org.observe.expresso.ops;

import java.util.List;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.expresso.ClassView;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableExpression.LiteralExpression;
import org.observe.expresso.ObservableExpression.MethodFinder;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public class ConditionalExpression implements ObservableExpression {
	private final ObservableExpression theCondition;
	private final ObservableExpression thePrimary;
	private final ObservableExpression theSecondary;

	public ConditionalExpression(ObservableExpression condition, ObservableExpression primary, ObservableExpression secondary) {
		theCondition = condition;
		thePrimary = primary;
		theSecondary = secondary;
	}

	public ObservableExpression getCondition() {
		return theCondition;
	}

	public ObservableExpression getPrimary() {
		return thePrimary;
	}

	public ObservableExpression getSecondary() {
		return theSecondary;
	}

	@Override
	public List<? extends ObservableExpression> getChildren() {
		return QommonsUtils.unmodifiableCopy(theCondition, thePrimary, theSecondary);
	}

	@Override
	public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ObservableModelSet models,
		ClassView classView) throws QonfigInterpretationException {
		if (type != null && (type.getModelType() == ModelTypes.Value || type.getModelType() == ModelTypes.Collection
			|| type.getModelType() == ModelTypes.Set)) {//
		} else
			throw new QonfigInterpretationException(
				"Conditional expressions not supported for model type " + type.getModelType() + " (" + this + ")");
		ValueContainer<SettableValue, SettableValue<Boolean>> conditionV = theCondition.evaluate(//
			ModelTypes.Value.forType(boolean.class), models, classView);
		ValueContainer<M, MV> primaryV = thePrimary.evaluate(type, models, classView);
		ValueContainer<M, MV> secondaryV = theSecondary.evaluate(type, models, classView);
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
				Object[] values = new Object[2];
				if (primaryV.getType().getModelType() == ModelTypes.Value) {
					return (MV) SettableValue.flattenAsSettable(conditionX.map(
						TypeTokens.get().keyFor(ObservableValue.class).<SettableValue<Object>> parameterized(resultType.getType(0)),
						c -> {
							if (c != null && c) {
								if (values[0] == null)
									values[0] = primaryV.get(msi);
								return (SettableValue<Object>) values[0];
							} else {
								if (values[1] == null)
									values[1] = secondaryV.get(msi);
								return (SettableValue<Object>) values[1];
							}
						}), null);
				} else if (primaryV.getType().getModelType() == ModelTypes.Collection) {
					return (MV) ObservableCollection.flattenValue(conditionX.map(TypeTokens.get().keyFor(ObservableCollection.class)
						.<ObservableCollection<Object>> parameterized(resultType.getType(0)), c -> {
							if (c != null && c) {
								if (values[0] == null)
									values[0] = primaryV.get(msi);
								return (ObservableCollection<Object>) values[0];
							} else {
								if (values[1] == null)
									values[1] = secondaryV.get(msi);
								return (ObservableCollection<Object>) values[1];
							}
						}));
				} else if (primaryV.getType().getModelType() == ModelTypes.Set) {
					return (MV) ObservableSet.flattenValue(conditionX.map(
						TypeTokens.get().keyFor(ObservableSet.class).<ObservableSet<Object>> parameterized(resultType.getType(0)),
						c -> {
							if (c != null && c) {
								if (values[0] == null)
									values[0] = primaryV.get(msi);
								return (ObservableSet<Object>) values[0];
							} else {
								if (values[1] == null)
									values[1] = secondaryV.get(msi);
								return (ObservableSet<Object>) values[1];
							}
						}));
				} else
					throw new IllegalStateException(
						"Conditional expressions not supported for model type " + primaryV.getType().getModelType());
			}
		};
	}

	@Override
	public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ObservableModelSet models,
		ClassView classView) throws QonfigInterpretationException {
		throw new QonfigInterpretationException("Not supported for conditionals");
	}

	@Override
	public String toString() {
		return theCondition + " ? " + thePrimary + " : " + theSecondary;
	}
}