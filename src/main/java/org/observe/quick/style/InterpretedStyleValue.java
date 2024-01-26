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
	 * @param attribute The style attribute this value is for
	 * @param value The value container
	 * @param modelContext The required model context for the style value
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

	/** @return The style attribute this value is for */
	public QuickStyleAttribute<T> getAttribute() {
		return theAttribute;
	}

	/** @return The value container */
	public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getValue() {
		return theValue;
	}

	/** @return The required model context for this style value */
	public ExWithRequiredModels.InterpretedRequiredModelContext getModelContext() {
		return theModelContext;
	}

	/**
	 * @param models The interpreted models to create the instantiator with
	 * @return An instantiator for this style value
	 */
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

	/**
	 * Instantiator for a style value's condition and value
	 *
	 * @param <T> The type of the style attribute
	 */
	public static class StyleValueInstantiator<T> {
		/** The model value instantiator for the style value's condition */
		public final ModelValueInstantiator<ObservableValue<Boolean>> condition;
		/** The model value instantiator for the style value's value */
		public final ModelValueInstantiator<SettableValue<T>> value;
		/** The required model context for the style value */
		public final ExWithRequiredModels.InterpretedRequiredModelContext modelContext;

		/**
		 * @param condition The model value instantiator for the style value's condition
		 * @param value The model value instantiator for the style value's value
		 * @param modelContext The required model context for the style value
		 */
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
