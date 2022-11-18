package org.observe.quick.style;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.StringUtils;
import org.qommons.config.QonfigInterpretationException;

/**
 * Represents a conditional value for a style attribute in quick
 *
 * @param <T> The type of the value
 */
public class QuickStyleValue<T> implements Comparable<QuickStyleValue<?>> {
	private final QuickStyleSheet theStyleSheet;
	private final StyleValueApplication theApplication;
	private final QuickStyleAttribute<T> theAttribute;
	private final ObservableExpression theValueExpression;

	/**
	 * @param styleSheet The style sheet that defined this value
	 * @param application The application of this value
	 * @param attribute THe attribute this value is for
	 * @param value The expression defining this style value's value
	 */
	public QuickStyleValue(QuickStyleSheet styleSheet, StyleValueApplication application, QuickStyleAttribute<T> attribute,
		ObservableExpression value) {
		theStyleSheet = styleSheet;
		theApplication = application;
		theAttribute = attribute;
		theValueExpression = value;
	}

	/** @return The style sheet that defined this value */
	public QuickStyleSheet getStyleSheet() {
		return theStyleSheet;
	}

	/**
	 * @return The application of this value, determining which elements it
	 *         {@link StyleValueApplication#applies(org.qommons.config.QonfigElement) applies} to and
	 *         {@link StyleValueApplication#getCondition() when}
	 */
	public StyleValueApplication getApplication() {
		return theApplication;
	}

	/** @return The style attribute this value is for */
	public QuickStyleAttribute<T> getAttribute() {
		return theAttribute;
	}

	/** @return The expression defining this style value's value */
	public ObservableExpression getValueExpression() {
		return theValueExpression;
	}

	/**
	 * @param expressoEnv The environment in which to evaluate this value
	 * @return An {@link EvaluatedStyleValue} of this style value evaluated for the given environment
	 * @throws QonfigInterpretationException If an error occurs
	 *         {@link ObservableExpression#evaluate(org.observe.expresso.ModelType.ModelInstanceType, ExpressoEnv) evaluating} this
	 *         {@link #getValueExpression() value} or its {@link StyleValueApplication#getCondition() condition}
	 */
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
}