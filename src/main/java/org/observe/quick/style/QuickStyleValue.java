package org.observe.quick.style;

import java.util.Map;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.LocatedExpression;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.qommons.StringUtils;
import org.qommons.config.QonfigInterpretationException;

/**
 * The definition of a conditional value for a style attribute in quick
 *
 * @param <T> The type of the value
 */
public class QuickStyleValue<T> implements Comparable<QuickStyleValue<?>> {
	private final QuickStyleSheet theStyleSheet;
	private final StyleApplicationDef theApplication;
	private final QuickStyleAttribute<T> theAttribute;
	private final LocatedExpression theValueExpression;

	/**
	 * @param styleSheet The style sheet that defined this value
	 * @param application The application of this value
	 * @param attribute THe attribute this value is for
	 * @param value The expression defining this style value's value
	 */
	public QuickStyleValue(QuickStyleSheet styleSheet, StyleApplicationDef application, QuickStyleAttribute<T> attribute,
		LocatedExpression value) {
		if (attribute == null)
			throw new NullPointerException("Attribute is null");
		else if (value == null)
			throw new NullPointerException("Value is null");
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
	 *         {@link StyleApplicationDef#applies(org.qommons.config.QonfigElement) applies} to and
	 *         {@link StyleApplicationDef#getCondition() when}
	 */
	public StyleApplicationDef getApplication() {
		return theApplication;
	}

	/** @return The style attribute this value is for */
	public QuickStyleAttribute<T> getAttribute() {
		return theAttribute;
	}

	/** @return The expression defining this style value's value */
	public LocatedExpression getValueExpression() {
		return theValueExpression;
	}

	/**
	 * @param expressoEnv The expresso environment in which to
	 *        {@link ObservableExpression#evaluate(org.observe.expresso.ModelType.ModelInstanceType, ExpressoEnv, int) evaluate} this
	 *        value's expressions when the compiled result is {@link CompiledStyleValue#interpret(Map) interpreted}
	 * @param applications A set of compiled style applications for re-use
	 * @return The compiled style value
	 * @throws QonfigInterpretationException If the expressions could not be compiled
	 */
	public CompiledStyleValue<T> compile(ExpressoEnv expressoEnv, Map<StyleApplicationDef, CompiledStyleApplication> applications)
		throws QonfigInterpretationException {
		CompiledStyleApplication application = theApplication.compile(expressoEnv, applications);
		CompiledModelValue<SettableValue<?>, SettableValue<T>> valueV = CompiledModelValue.of(theAttribute.getName(), ModelTypes.Value,
			() -> theValueExpression.evaluate(ModelTypes.Value.forType(theAttribute.getType()), expressoEnv));
		return new CompiledStyleValue<>(this, application, valueV);
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