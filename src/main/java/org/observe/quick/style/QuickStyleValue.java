package org.observe.quick.style;

import java.util.List;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.StringUtils;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigInterpretationException;

/** Represents a conditional value for a style attribute in quick */
public class QuickStyleValue<T> implements Comparable<QuickStyleValue<?>> {
	private final QuickStyleSheet theStyleSheet;
	private final StyleValueApplication theApplication;
	private final QuickStyleAttribute<T> theAttribute;
	private final ObservableExpression theValueExpression;

	public QuickStyleValue(QuickStyleSheet styleSheet, StyleValueApplication application, QuickStyleAttribute<T> attribute,
		ObservableExpression value)
			throws QonfigInterpretationException {
		theStyleSheet = styleSheet;
		theApplication = application;
		theAttribute = attribute;
		theValueExpression = value;
	}

	public QuickStyleSheet getStyleSheet() {
		return theStyleSheet;
	}

	public StyleValueApplication getApplication() {
		return theApplication;
	}

	public QuickStyleAttribute<T> getAttribute() {
		return theAttribute;
	}

	public ObservableExpression getValueExpression() {
		return theValueExpression;
	}

	public EvaluatedStyleValue<T> evaluate(ExpressoEnv expressoEnv) throws QonfigInterpretationException {
		EvaluatedStyleApplication application = theApplication.evaluate(expressoEnv);
		ValueContainer<SettableValue<?>, SettableValue<T>> valueV = theValueExpression
			.evaluate(ModelTypes.Value.forType(theAttribute.getType()), expressoEnv);
		return new EvaluatedStyleValue<>(this, application, valueV);
	}

	@Override
	public int compareTo(QuickStyleValue<?> o) {
		int comp = theApplication.compareTo(o.theApplication);
		// Compare the source style sheets
		if (comp == 0) {
			if (theStyleSheet == null) {
				if (o.theStyleSheet != null)
					comp = -1; // Style values specified on the element have highest priority
			} else if (o.theStyleSheet == null)
				comp = 1; // Style values specified on the element have highest priority
		}
		// Last, compare the attributes, just multiple attribute values with identical priority
		// can live in the same style sheet together
		if (comp == 0)
			comp = StringUtils.compareNumberTolerant(//
				theAttribute.getDeclarer().getElement().getDeclarer().getLocation().toString(), //
				o.theAttribute.getDeclarer().getElement().getDeclarer().getLocation().toString(), true, true);
		if (comp == 0)
			comp = StringUtils.compareNumberTolerant(//
				theAttribute.getDeclarer().getElement().getName(), //
				o.theAttribute.getDeclarer().getElement().getName(), true, true);
		if (comp == 0)
			comp = StringUtils.compareNumberTolerant(theAttribute.getName(), o.theAttribute.getName(), true, true);
		return comp;
	}

	@Override
	public String toString() {
		return new StringBuilder(theApplication.toString()).append(':').append(theAttribute.getName()).append('=')
			.append(theValueExpression).toString();
	}

	public static StringBuilder printRolePath(List<QonfigChildDef> rolePath, StringBuilder str) {
		if (str == null)
			str = new StringBuilder();
		str.append(rolePath.get(0).getOwner().getName());
		for (QonfigChildDef role : rolePath)
			str.append('.').append(role.getName());
		return str;
	}
}