package org.observe.quick.style;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
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

	/**
	 * @param model The model instance to evaluate the condition for
	 * @return The condition returning whether and when this applies to the element
	 * @throws ModelInstantiationException If the condition could not be evaluated
	 */
	public ObservableValue<Boolean> getCondition(ModelSetInstance model) throws ModelInstantiationException {
		ObservableValue<Boolean> parentCond;
		if (theParent == null)
			parentCond = TRUE;
		else
			parentCond = theParent.getCondition(getParentModels(model));

		ObservableValue<Boolean> localCond;
		if (theCondition != null)
			localCond = theCondition.get(model);
		else
			localCond = TRUE;

		if (TRUE.equals(parentCond))
			return localCond;
		else if (TRUE.equals(localCond))
			return parentCond;
		else
			return parentCond.transform(boolean.class, tx -> tx.combineWith(localCond)//
				.combine(LambdaUtils.printableBiFn((c1, c2) -> c1 && c2, "&&", "&&")));
	}

	@Override
	public String toString() {
		return theDefinition.toString();
	}

	/**
	 * @param models The model instance to get the parent model from
	 * @return The model instance for the parent &lt;styled> element
	 */
	public static ModelSetInstance getParentModels(ModelSetInstance models) {
		try {
			return ((SettableValue<ModelSetInstance>) models.getModel().getComponent(PARENT_MODEL_NAME).interpreted().get(models)).get();
		} catch (ModelException | ExpressoInterpretationException | ModelInstantiationException | ClassCastException
			| IllegalStateException e) {
			throw new IllegalStateException("Could not access parent models. Perhaps they have not been installed.", e);
		}
	}
}
