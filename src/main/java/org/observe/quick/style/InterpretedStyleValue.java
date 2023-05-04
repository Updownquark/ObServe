package org.observe.quick.style;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;

/**
 * A {@link QuickStyleValue style value} evaluated for an {@link ExpressoEnv environment}
 *
 * @param <T> The type of the value
 */
public class InterpretedStyleValue<T> implements Comparable<InterpretedStyleValue<?>> {
	private final QuickStyleValue<T> theStyleValue;
	private final InterpretedStyleApplication theApplication;
	private final ModelValueSynth<SettableValue<?>, SettableValue<T>> theValue;

	/**
	 * @param styleValue The style value this structure is evaluated from
	 * @param application The application for this value
	 * @param value The value container
	 */
	public InterpretedStyleValue(QuickStyleValue<T> styleValue, InterpretedStyleApplication application,
		ModelValueSynth<SettableValue<?>, SettableValue<T>> value) {
		theStyleValue = styleValue;
		theApplication = application;
		theValue = value;
	}

	/** @return The style value this structure is evaluated from */
	public QuickStyleValue<T> getStyleValue() {
		return theStyleValue;
	}

	/** @return The application for this value */
	public InterpretedStyleApplication getApplication() {
		return theApplication;
	}

	/** @return The value container */
	public ModelValueSynth<SettableValue<?>, SettableValue<T>> getValue() {
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
