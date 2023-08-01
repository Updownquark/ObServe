package org.observe.quick.style;

import org.observe.SettableValue;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;

/**
 * A {@link QuickStyleValue style value} evaluated for an {@link InterpretedExpressoEnv environment}
 *
 * @param <T> The type of the value
 */
public class InterpretedStyleValue<T> implements Comparable<InterpretedStyleValue<?>> {
	private final QuickStyleValue theStyleValue;
	private final InterpretedStyleApplication theApplication;
	private final QuickStyleAttribute<T> theAttribute;
	private final InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theValue;

	/**
	 * @param styleValue The style value this structure is evaluated from
	 * @param application The application for this value
	 * @param value The value container
	 */
	public InterpretedStyleValue(QuickStyleValue styleValue, InterpretedStyleApplication application, QuickStyleAttribute<T> attribute,
		InterpretedValueSynth<SettableValue<?>, SettableValue<T>> value) {
		theStyleValue = styleValue;
		theApplication = application;
		theAttribute = attribute;
		theValue = value;
	}

	/** @return The style value this structure is evaluated from */
	public QuickStyleValue getStyleValue() {
		return theStyleValue;
	}

	/** @return The application for this value */
	public InterpretedStyleApplication getApplication() {
		return theApplication;
	}

	public QuickStyleAttribute<T> getAttribute() {
		return theAttribute;
	}

	/** @return The value container */
	public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue() {
		return theValue;
	}

	@Override
	public int compareTo(InterpretedStyleValue<?> o) {
		return theStyleValue.compareTo(o.theStyleValue);
	}

	@Override
	public String toString() {
		return theStyleValue.toString();
	}
}
