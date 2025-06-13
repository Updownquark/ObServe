package org.observe.quick.style;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.LocatedExpression;
import org.qommons.ArrayUtils;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;

/** A {@link StyleApplicationDef} evaluated for an {@link InterpretedExpressoEnv environment} */
public class InterpretedStyleApplication {
	/** Constant value returning true */
	public static final ObservableValue<Boolean> TRUE = ObservableValue.of(true);

	private final InterpretedStyleApplication theParent;
	private final StyleApplicationDef theDefinition;
	private final Map<LocatedExpression, InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>>> theConditions;

	/**
	 * @param parent The parent application (see {@link StyleApplicationDef#getParent()})
	 * @param definition The application definition this structure is interpreted from
	 * @param conditions The style conditions
	 */
	public InterpretedStyleApplication(InterpretedStyleApplication parent, StyleApplicationDef definition,
		Map<LocatedExpression, InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>>> conditions) {
		theParent = parent;
		theDefinition = definition;
		theConditions = conditions;
	}

	/** @return The parent application (see {@link StyleApplicationDef#getParent()}) */
	public InterpretedStyleApplication getParent() {
		return theParent;
	}

	/**
	 * @return The application definition this structure is
	 *         {@link StyleApplicationDef#interpret(InterpretedStyleApplication, org.observe.expresso.qonfig.ExElement.Interpreted)
	 *         interpreted} from
	 */
	public StyleApplicationDef getDefinition() {
		return theDefinition;
	}

	/** @return The style conditions */
	public Map<LocatedExpression, InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>>> getConditions() {
		return theConditions;
	}

	/**
	 * @return An instantiator for the condition returning whether and when this applies to the element
	 * @throws ModelInstantiationException If the condition cannot be instantiated
	 */
	public ModelValueInstantiator<ObservableValue<Boolean>> getConditionInstantiator()
		throws ModelInstantiationException {
		return new Instantiator(theParent == null ? null : theParent.getConditionInstantiator(), //
			QommonsUtils.filterMapE(theConditions.values(), null, c -> c.instantiate()));
	}

	@Override
	public String toString() {
		return theDefinition.toString();
	}

	/** An instantiator for a style element's condition */
	public static class Instantiator implements ModelValueInstantiator<ObservableValue<Boolean>> {
		private final ModelValueInstantiator<? extends ObservableValue<Boolean>> theParentCondition;
		private final List<ModelValueInstantiator<? extends ObservableValue<Boolean>>> theConditions;

		/**
		 * @param parentCondition The parent element's condition
		 * @param conditions The style element's conditions
		 */
		public Instantiator(ModelValueInstantiator<? extends ObservableValue<Boolean>> parentCondition,
			List<ModelValueInstantiator<? extends ObservableValue<Boolean>>> conditions) {
			theParentCondition = parentCondition;
			theConditions = conditions;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			if(theParentCondition!=null)
				theParentCondition.instantiate();
			for (ModelValueInstantiator<? extends ObservableValue<Boolean>> condition : theConditions)
				condition.instantiate();
		}

		@Override
		public ObservableValue<Boolean> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			ObservableValue<Boolean> parentCond;
			if (theParentCondition == null)
				parentCond = TRUE;
			else
				parentCond = theParentCondition.get(models);

			ObservableValue<Boolean>[] localCond = new ObservableValue[theConditions.size()];
			for (int i = 0; i < localCond.length; i++)
				localCond[i] = theConditions.get(i).get(models);

			return combine(parentCond, localCond);
		}

		static final Predicate<Boolean> NOT_TRUE = LambdaUtils.printablePred(v -> !Boolean.TRUE.equals(v), "not true", null);
		static final Supplier<Boolean> GET_TRUE = LambdaUtils.constantSupplier(true, "true", true);

		ObservableValue<Boolean> combine(ObservableValue<Boolean> parentCond, ObservableValue<Boolean>[] localCond) {
			if (localCond.length == 0)
				return parentCond;
			else if (parentCond.getThreadConstraint() == ThreadConstraint.NONE) {
				if (Boolean.TRUE.equals(parentCond.get()))
					return evalLocal(localCond);
				else
					return parentCond;
			} else {
				return ObservableValue.firstValue(NOT_TRUE, GET_TRUE, ArrayUtils.add(localCond, 0, parentCond));
			}
		}

		private ObservableValue<Boolean> evalLocal(ObservableValue<Boolean>[] localCond) {
			if (localCond.length == 1)
				return localCond[0];
			else
				return ObservableValue.firstValue(NOT_TRUE, GET_TRUE, localCond);
		}

		@Override
		public ObservableValue<Boolean> forModelCopy(ObservableValue<Boolean> value, ModelSetInstance sourceModels,
			ModelSetInstance newModels) throws ModelInstantiationException {
			ObservableValue<Boolean> sourceParent = theParentCondition == null ? TRUE : theParentCondition.get(sourceModels);
			ObservableValue<Boolean> newParent = theParentCondition == null ? TRUE
				: ((ModelValueInstantiator<ObservableValue<Boolean>>) theParentCondition).forModelCopy(sourceParent, sourceModels,
					newModels);
			ObservableValue<Boolean>[] sourceLocal = new ObservableValue[theConditions.size()];
			ObservableValue<Boolean>[] newLocal = new ObservableValue[theConditions.size()];
			boolean same = sourceParent == newParent;
			for (int i = 0; i < sourceLocal.length; i++) {
				sourceLocal[i] = theConditions.get(i).get(sourceModels);
				newLocal[i] = ((ModelValueInstantiator<ObservableValue<Boolean>>) theConditions.get(i)).forModelCopy(sourceLocal[i],
					sourceModels, newModels);
				same &= sourceLocal[i] == newLocal[i];
			}

			if (same)
				return value;
			else
				return combine(newParent, newLocal);
		}

		@Override
		public String toString() {
			if (theParentCondition != null) {
				if (theConditions != null)
					return theParentCondition + " && " + theConditions;
				else
					return theParentCondition.toString();
			} else if (theConditions != null)
				return theConditions.toString();
			else
				return "true";
		}
	}

	/**
	 * @param models The model instance to get the parent model from
	 * @param parentModelValue The model ID of the model value containing the parent style element's models
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
