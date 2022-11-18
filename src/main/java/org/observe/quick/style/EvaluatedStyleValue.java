package org.observe.quick.style;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ObservableModelSet.ValueContainer;

/**
 * A {@link QuickStyleValue style value} evaluated for an {@link ExpressoEnv environment}
 * 
 * @param <T> The type of the value
 */
public class EvaluatedStyleValue<T> implements Comparable<EvaluatedStyleValue<?>> {
	private final QuickStyleValue<T> theStyleValue;
	private final EvaluatedStyleApplication theApplication;
	private final ValueContainer<SettableValue<?>, SettableValue<T>> theValue;

	/**
	 * @param styleValue The style value this structure is evaluated from
	 * @param application The application for this value
	 * @param value The value container
	 */
	public EvaluatedStyleValue(QuickStyleValue<T> styleValue, EvaluatedStyleApplication application,
		ValueContainer<SettableValue<?>, SettableValue<T>> value) {
		theStyleValue = styleValue;
		theApplication = application;
		theValue = value;
	}

	/** @return The style value this structure is evaluated from */
	public QuickStyleValue<T> getStyleValue() {
		return theStyleValue;
	}

	/** @return The application for this value */
	public EvaluatedStyleApplication getApplication() {
		return theApplication;
	}

	/** @return The value container */
	public ValueContainer<SettableValue<?>, SettableValue<T>> getValue() {
		return theValue;
	}

	@Override
	public int compareTo(EvaluatedStyleValue<?> o) {
		return theStyleValue.compareTo(o.theStyleValue);
	}
}
