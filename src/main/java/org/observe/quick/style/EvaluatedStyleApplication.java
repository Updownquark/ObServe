package org.observe.quick.style;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.LambdaUtils;

/** A {@link StyleValueApplication} evaluated for an {@link ExpressoEnv environment} */
public class EvaluatedStyleApplication {
	/** Constant value returning true */
	public static final ObservableValue<Boolean> TRUE = ObservableValue.of(boolean.class, true);

	private final EvaluatedStyleApplication theParent;
	private final StyleValueApplication theApplication;
	private final ValueContainer<SettableValue<?>, SettableValue<Boolean>> theCondition;

	/**
	 * @param parent The parent application (see {@link StyleValueApplication#getParent()})
	 * @param application The application this structure is evaluated from
	 * @param condition The condition value container
	 */
	public EvaluatedStyleApplication(EvaluatedStyleApplication parent, StyleValueApplication application,
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> condition) {
		theParent = parent;
		theApplication = application;
		theCondition = condition;
	}

	/** @return The parent application (see {@link StyleValueApplication#getParent()}) */
	public EvaluatedStyleApplication getParent() {
		return theParent;
	}

	/** @return The application this structure is evaluated from */
	public StyleValueApplication getApplication() {
		return theApplication;
	}

	/** @return The condition value container */
	public ValueContainer<SettableValue<?>, SettableValue<Boolean>> getCondition() {
		return theCondition;
	}

	/**
	 * @param model The model instance to evaluate the condition for
	 * @return The condition returning whether and when this applies to the element
	 */
	public ObservableValue<Boolean> getCondition(ModelSetInstance model) {
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
