package org.observe.quick.style;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.qommons.LambdaUtils;

/** A {@link StyleApplicationDef} evaluated for an {@link ExpressoEnv environment} */
public class InterpretedStyleApplication {
	/** Constant value returning true */
	public static final ObservableValue<Boolean> TRUE = ObservableValue.of(boolean.class, true);

	private final InterpretedStyleApplication theParent;
	private final CompiledStyleApplication theApplication;
	private final ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> theCondition;

	/**
	 * @param parent The parent application (see {@link StyleApplicationDef#getParent()})
	 * @param application The application this structure is evaluated from
	 * @param condition The condition value container
	 */
	public InterpretedStyleApplication(InterpretedStyleApplication parent, CompiledStyleApplication application,
		ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> condition) {
		theParent = parent;
		theApplication = application;
		theCondition = condition;
	}

	/** @return The parent application (see {@link StyleApplicationDef#getParent()}) */
	public InterpretedStyleApplication getParent() {
		return theParent;
	}

	/** @return The application this structure is {@link CompiledStyleApplication#interpret(java.util.Map) interpreted} from */
	public CompiledStyleApplication getApplication() {
		return theApplication;
	}

	/** @return The condition value container */
	public ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> getCondition() {
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
			parentCond = theParent.getCondition(StyleQIS.getParentModels(model));

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
		return theApplication.toString();
	}
}
