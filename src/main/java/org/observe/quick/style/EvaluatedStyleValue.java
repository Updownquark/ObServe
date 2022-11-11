package org.observe.quick.style;

import org.observe.SettableValue;
import org.observe.expresso.ObservableModelSet.ValueContainer;

public class EvaluatedStyleValue<T> implements Comparable<EvaluatedStyleValue<?>> {
	private final QuickStyleValue<T> theStyleValue;
	private final EvaluatedStyleApplication theApplication;
	private final ValueContainer<SettableValue<?>, SettableValue<T>> theValue;

	public EvaluatedStyleValue(QuickStyleValue<T> styleValue, EvaluatedStyleApplication application,
		ValueContainer<SettableValue<?>, SettableValue<T>> value) {
		theStyleValue = styleValue;
		theApplication = application;
		theValue = value;
	}

	public QuickStyleValue<T> getStyleValue() {
		return theStyleValue;
	}

	public EvaluatedStyleApplication getApplication() {
		return theApplication;
	}

	public ValueContainer<SettableValue<?>, SettableValue<T>> getValue() {
		return theValue;
	}

	@Override
	public int compareTo(EvaluatedStyleValue<?> o) {
		return theStyleValue.compareTo(o.theStyleValue);
	}
}
