package org.observe.quick.style;

import java.util.Map;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;

/**
 * A structure {@link QuickStyleValue#compile(org.observe.expresso.ExpressoEnv, Map) compiled} from a {@link QuickStyleValue} containing
 * information defining a conditional style value
 *
 * @param <T> The type of the style attribute
 */
public class CompiledStyleValue<T> implements Comparable<CompiledStyleValue<?>> {
	private final QuickStyleValue<T> theStyleValue;
	private final CompiledStyleApplication theApplication;
	private final CompiledModelValue<SettableValue<?>, SettableValue<T>> theValue;

	/**
	 * @param styleValue The style value this structure is evaluated from
	 * @param application The application for this value
	 * @param value The value
	 */
	public CompiledStyleValue(QuickStyleValue<T> styleValue, CompiledStyleApplication application,
		CompiledModelValue<SettableValue<?>, SettableValue<T>> value) {
		theStyleValue = styleValue;
		theApplication = application;
		theValue = value;
	}

	/** @return The style value this structure is evaluated from */
	public QuickStyleValue<T> getStyleValue() {
		return theStyleValue;
	}

	/** @return The application for this value */
	public CompiledStyleApplication getApplication() {
		return theApplication;
	}

	/** @return The value */
	public CompiledModelValue<SettableValue<?>, SettableValue<T>> getValue() {
		return theValue;
	}

	/**
	 * Interprets this compiled structure
	 *
	 * @param applications A cache of interpreted style applications for re-use
	 * @return The interpreted style value
	 * @throws ExpressoInterpretationException If this structure's expressions could not be
	 *         {@link ObservableExpression#evaluate(org.observe.expresso.ModelType.ModelInstanceType, org.observe.expresso.ExpressoEnv, int)
	 *         evaluated}
	 */
	public InterpretedStyleValue<T> interpret(Map<CompiledStyleApplication, InterpretedStyleApplication> applications)
		throws ExpressoInterpretationException {
		InterpretedStyleApplication application;
		if (theApplication == null)
			application = null;
		else {
			application = applications.get(theApplication);
			if (application == null) {
				application = theApplication.interpret(applications);
				applications.put(theApplication, application);
			}
		}
		return new InterpretedStyleValue<>(theStyleValue, //
			application, theValue.createSynthesizer());
	}

	@Override
	public int compareTo(CompiledStyleValue<?> o) {
		return theStyleValue.compareTo(o.theStyleValue);
	}
}
