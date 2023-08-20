package org.observe.quick.style;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.qommons.LambdaUtils;

/** A {@link StyleApplicationDef} evaluated for an {@link InterpretedExpressoEnv environment} */
public class InterpretedStyleApplication {
	/** Constant value returning true */
	public static final ObservableValue<Boolean> TRUE = ObservableValue.of(boolean.class, true);

	public static final String PARENT_MODEL_NAME = "PARENT$MODEL$INSTANCE";
	private static final ModelInstanceType<SettableValue<?>, SettableValue<ModelSetInstance>> PARENT_MODEL_TYPE = ModelTypes.Value
		.forType(ModelSetInstance.class);

	private final InterpretedStyleApplication theParent;
	private final StyleApplicationDef theDefinition;
	private final InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> theCondition;

	/**
	 * @param parent The parent application (see {@link StyleApplicationDef#getParent()})
	 * @param definition The application definition this structure is interpreted from
	 * @param condition The condition value container
	 */
	public InterpretedStyleApplication(InterpretedStyleApplication parent, StyleApplicationDef definition,
		InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> condition) {
		theParent = parent;
		theDefinition = definition;
		theCondition = condition;
	}

	/** @return The parent application (see {@link StyleApplicationDef#getParent()}) */
	public InterpretedStyleApplication getParent() {
		return theParent;
	}

	/**
	 * @return The application definition this structure is
	 *         {@link StyleApplicationDef#interpret(InterpretedExpressoEnv, QuickInterpretedStyleCache.Applications) interpreted} from
	 */
	public StyleApplicationDef getDefinition() {
		return theDefinition;
	}

	/** @return The condition value container */
	public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> getCondition() {
		return theCondition;
	}

	/** @return An instantiator for the condition returning whether and when this applies to the element */
	public ModelValueInstantiator<ObservableValue<Boolean>> getConditionInstantiator(InterpretedModelSet models) {
		ModelComponentId parentModelValue;
		try {
			parentModelValue = models.getComponent(PARENT_MODEL_NAME).getIdentity();
		} catch (ModelException e) {
			throw new IllegalArgumentException("Could not get parent model value", e);
		}
		return new Instantiator(theParent == null ? null : theParent.getConditionInstantiator(StyleApplicationDef.getParentModel(models)),
			theCondition == null ? null : theCondition.instantiate(), parentModelValue);
	}

	@Override
	public String toString() {
		return theDefinition.toString();
	}

	public static class Instantiator implements ModelValueInstantiator<ObservableValue<Boolean>> {
		private final ModelValueInstantiator<? extends ObservableValue<Boolean>> theParentCondition;
		private final ModelValueInstantiator<? extends ObservableValue<Boolean>> theCondition;
		private final ModelComponentId theParentModelValue;

		public Instantiator(ModelValueInstantiator<? extends ObservableValue<Boolean>> parentCondition,
			ModelValueInstantiator<? extends ObservableValue<Boolean>> condition, ModelComponentId parentModelValue) {
			theParentCondition = parentCondition;
			theCondition = condition;
			theParentModelValue = parentModelValue;
		}

		@Override
		public ObservableValue<Boolean> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			ObservableValue<Boolean> parentCond;
			if (theParentCondition == null)
				parentCond = TRUE;
			else
				parentCond = theParentCondition.get(getParentModels(models, theParentModelValue));

			ObservableValue<Boolean> localCond;
			if (theCondition != null)
				localCond = theCondition.get(models);
			else
				localCond = TRUE;

			return combine(parentCond, localCond);
		}

		ObservableValue<Boolean> combine(ObservableValue<Boolean> parentCond, ObservableValue<Boolean> localCond) {
			if (TRUE.equals(parentCond))
				return localCond;
			else if (TRUE.equals(localCond))
				return parentCond;
			else
				return parentCond.transform(boolean.class, tx -> tx.combineWith(localCond)//
					.combine(LambdaUtils.printableBiFn((c1, c2) -> c1 && c2, "&&", "&&")));
		}

		@Override
		public ObservableValue<Boolean> forModelCopy(ObservableValue<Boolean> value, ModelSetInstance sourceModels,
			ModelSetInstance newModels) throws ModelInstantiationException {
			ObservableValue<Boolean> sourceParent = theParentCondition == null ? TRUE : theParentCondition.get(sourceModels);
			ObservableValue<Boolean> newParent = theParentCondition == null ? TRUE
				: ((ModelValueInstantiator<ObservableValue<Boolean>>) theParentCondition).forModelCopy(sourceParent, sourceModels,
					newModels);
			ObservableValue<Boolean> sourceLocal = theCondition == null ? TRUE : theCondition.get(sourceModels);
			ObservableValue<Boolean> newLocal = theCondition == null ? TRUE
				: ((ModelValueInstantiator<ObservableValue<Boolean>>) theCondition).forModelCopy(sourceLocal, sourceModels, newModels);

			if (sourceParent == newParent && sourceLocal == newLocal)
				return value;
			else
				return combine(newParent, newLocal);
		}

		@Override
		public String toString() {
			if (theParentCondition != null) {
				if (theCondition != null)
					return theParentCondition + " && " + theCondition;
				else
					return theParentCondition.toString();
			} else if (theCondition != null)
				return theCondition.toString();
			else
				return "true";
		}
	}

	/**
	 * @param models The model instance to get the parent model from
	 * @return The model instance for the parent &lt;styled> element
	 */
	public static ModelSetInstance getParentModels(ModelSetInstance models, ModelComponentId parentModelValue) {
		try {
			return ((SettableValue<ModelSetInstance>) models.get(parentModelValue)).get();
		} catch (ModelInstantiationException | ClassCastException | IllegalArgumentException | IllegalStateException e) {
			throw new IllegalStateException("Could not access parent models. Perhaps they have not been installed.", e);
		}
	}
}
