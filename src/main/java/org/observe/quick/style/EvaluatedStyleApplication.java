package org.observe.quick.style;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.LambdaUtils;

public class EvaluatedStyleApplication {
	public static final ObservableValue<Boolean> TRUE = ObservableValue.of(boolean.class, true);

	private final EvaluatedStyleApplication theParent;
	private final StyleValueApplication theApplication;
	private final ValueContainer<SettableValue<?>, SettableValue<Boolean>> theCondition;

	public EvaluatedStyleApplication(EvaluatedStyleApplication parent, StyleValueApplication application,
		ValueContainer<SettableValue<?>, SettableValue<Boolean>> condition) {
		theParent = parent;
		theApplication = application;
		theCondition = condition;
	}

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
}
