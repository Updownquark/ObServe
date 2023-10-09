package org.observe.quick.style;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExWithRequiredModels;
import org.observe.expresso.qonfig.ExWithRequiredModels.InterpretedRequiredModelContext;

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
	private final ExWithRequiredModels.InterpretedRequiredModelContext theModelContext;

	/**
	 * @param styleValue The style value this structure is evaluated from
	 * @param application The application for this value
	 * @param value The value container
	 * @param modelContext
	 */
	public InterpretedStyleValue(QuickStyleValue styleValue, InterpretedStyleApplication application, QuickStyleAttribute<T> attribute,
		InterpretedValueSynth<SettableValue<?>, SettableValue<T>> value, InterpretedRequiredModelContext modelContext) {
		theStyleValue = styleValue;
		theApplication = application;
		theAttribute = attribute;
		theValue = value;
		theModelContext = modelContext;
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

	public StyleValueInstantiator<T> instantiate(InterpretedModelSet models) {
		return new StyleValueInstantiator<>(theApplication.getConditionInstantiator(models), theValue.instantiate(), theModelContext);
	}

	@Override
	public int compareTo(InterpretedStyleValue<?> o) {
		return theStyleValue.compareTo(o.theStyleValue);
	}

	@Override
	public String toString() {
		return theStyleValue.toString();
	}

	public static class StyleValueInstantiator<T> {
		public final ModelValueInstantiator<ObservableValue<Boolean>> condition;
		public final ModelValueInstantiator<SettableValue<T>> value;
		public final ExWithRequiredModels.InterpretedRequiredModelContext modelContext;

		public StyleValueInstantiator(ModelValueInstantiator<ObservableValue<Boolean>> condition,
			ModelValueInstantiator<SettableValue<T>> value, ExWithRequiredModels.InterpretedRequiredModelContext modelContext) {
			this.condition = condition;
			this.value = value;
			this.modelContext = modelContext;
		}

		void instantiate() {
			if (condition != null)
				condition.instantiate();
			value.instantiate();
		}

		@Override
		public String toString() {
			return condition + " -> " + value;
		}
	}
}
