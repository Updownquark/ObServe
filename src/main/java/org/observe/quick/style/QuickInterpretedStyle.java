package org.observe.quick.style;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.QuickStyleElement.Interpreted;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.config.QonfigElement;

import com.google.common.reflect.TypeToken;

/** Represents style information for a {@link QonfigElement} */
public interface QuickInterpretedStyle {
	/** @return This element's {@link QonfigElement#getParent() parent}'s style */
	QuickInterpretedStyle getParent();

	/** @return All style values that may apply to this style */
	List<InterpretedStyleValue<?>> getDeclaredValues();

	/** @return All style attributes that apply to this element */
	public Set<QuickStyleAttribute<?>> getAttributes();

	/**
	 * @param <T> The type of the attribute
	 * @param attr The attribute to get the value for
	 * @return A structure containing all information necessary to get the value of a style attribute for this element
	 */
	<T> QuickElementStyleAttribute<T> get(QuickStyleAttribute<T> attr);

	List<QuickStyleElement.Interpreted> getStyleElements();

	void update() throws ExpressoInterpretationException;

	/** Default implementation */
	public class Default implements QuickInterpretedStyle {
		private final QuickInterpretedStyle theParent;
		private final List<InterpretedStyleValue<?>> theDeclaredValues;
		private final Map<QuickStyleAttribute<?>, QuickElementStyleAttribute<?>> theValues;
		private final List<QuickStyleElement.Interpreted> theStyleElements;

		/**
		 * @param parent The element style for the {@link QonfigElement#getParent() parent} element
		 * @param declaredValues All style values declared specifically on this element
		 * @param values All values for style attributes that vary on this style
		 */
		public Default(QuickInterpretedStyle parent, List<InterpretedStyleValue<?>> declaredValues,
			Map<QuickStyleAttribute<?>, QuickElementStyleAttribute<?>> values, List<QuickStyleElement.Interpreted> styleElements) {
			theParent = parent;
			theDeclaredValues = declaredValues;
			theValues = values;
			theStyleElements = styleElements;
		}

		@Override
		public QuickInterpretedStyle getParent() {
			return theParent;
		}

		@Override
		public List<InterpretedStyleValue<?>> getDeclaredValues() {
			return theDeclaredValues;
		}

		@Override
		public Set<QuickStyleAttribute<?>> getAttributes() {
			return theValues.keySet();
		}

		@Override
		public <T> QuickElementStyleAttribute<T> get(QuickStyleAttribute<T> attr) {
			QuickElementStyleAttribute<T> value = (QuickElementStyleAttribute<T>) theValues.get(attr);
			if (value != null)
				return value;
			return new QuickElementStyleAttribute<>(attr, this, Collections.emptyList(), //
				theParent != null && attr.isTrickleDown() ? theParent.get(attr) : null);
		}

		@Override
		public List<QuickStyleElement.Interpreted> getStyleElements() {
			return theStyleElements;
		}

		@Override
		public void update() throws ExpressoInterpretationException {
			for (QuickStyleElement.Interpreted styleEl : theStyleElements)
				styleEl.update();
		}
	}

	/**
	 * <p>
	 * A wrapper around another {@link QuickInterpretedStyle} that delegates most of its methods to the wrapped style.
	 * </p>
	 * <p>
	 * This serves as an abstract class for style extensions which provide added utility over a standard {@link QuickInterpretedStyle}.
	 * </p>
	 */
	public abstract class Wrapper implements QuickInterpretedStyle {
		private final QuickInterpretedStyle theParent;
		private final QuickInterpretedStyle theWrapped;

		/**
		 * @param parent The parent for this style
		 * @param wrapped The style to wrap
		 */
		protected Wrapper(QuickInterpretedStyle parent, QuickInterpretedStyle wrapped) {
			theParent = parent;
			theWrapped = wrapped;
		}

		@Override
		public QuickInterpretedStyle getParent() {
			return theParent;
		}

		@Override
		public List<InterpretedStyleValue<?>> getDeclaredValues() {
			return theWrapped.getDeclaredValues();
		}

		@Override
		public Set<QuickStyleAttribute<?>> getAttributes() {
			return theWrapped.getAttributes();
		}

		@Override
		public <T> QuickElementStyleAttribute<T> get(QuickStyleAttribute<T> attr) {
			return theWrapped.get(attr);
		}

		@Override
		public List<Interpreted> getStyleElements() {
			return theWrapped.getStyleElements();
		}

		@Override
		public void update() throws ExpressoInterpretationException {
			theWrapped.update();
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/**
	 * A structure containing all information necessary to get the value of a style attribute for an element
	 *
	 * @param <T> The type of the attribute
	 */
	public static class QuickElementStyleAttribute<T> {
		private final QuickStyleAttribute<T> theAttribute;
		private final QuickInterpretedStyle theStyle;
		private final List<InterpretedStyleValue<T>> theValues;
		private final QuickElementStyleAttribute<T> theInherited;

		/**
		 * @param attribute The attribute this structure is for
		 * @param style The element style this structure is for
		 * @param values All style values that may apply to the element for the attribute
		 * @param inherited The structure for the same attribute for the {@link QuickInterpretedStyle#getParent() parent} style
		 */
		public QuickElementStyleAttribute(QuickStyleAttribute<T> attribute, QuickInterpretedStyle style, List<InterpretedStyleValue<T>> values,
			QuickElementStyleAttribute<T> inherited) {
			theAttribute = attribute;
			theStyle = style;
			theValues = values;
			theInherited = inherited;
		}

		/** @return The attribute this structure is for */
		public QuickStyleAttribute<T> getAttribute() {
			return theAttribute;
		}

		/** @return The element style this structure is for */
		public QuickInterpretedStyle getStyle() {
			return theStyle;
		}

		/** @return All style values that may apply to the element for the attribute */
		public List<InterpretedStyleValue<T>> getValues() {
			return theValues;
		}

		/** @return The structure for the same attribute for the {@link QuickInterpretedStyle#getParent() parent} style */
		public QuickElementStyleAttribute<T> getInherited() {
			return theInherited;
		}

		/**
		 * @param models The model instance to get the value for
		 * @return The value for this style attribute on the element
		 * @throws ModelInstantiationException If the condition or the value could not be evaluated
		 */
		public ObservableValue<T> evaluate(ModelSetInstance models) throws ModelInstantiationException {
			ObservableValue<ConditionalValue<T>>[] values = new ObservableValue[theValues.size() + (theInherited == null ? 0 : 1)];
			for (int i = 0; i < theValues.size(); i++) {
				ObservableValue<Boolean> condition = theValues.get(i).getApplication().getCondition(models);
				SettableValue<? extends T> value = theValues.get(i).getValue().get(models);
				values[i] = condition.map(LambdaUtils.printableFn(pass -> new ConditionalValue<>(Boolean.TRUE.equals(pass), value),
					"ifPass(" + value + ")", null));
			}
			if (theInherited != null) {
				ObservableValue<T> value = theInherited.evaluate(StyleQIS.getParentModels(models));
				values[theValues.size()] = ObservableValue.of(new ConditionalValue<>(true, value));
			}
			ConditionalValue<T> defaultCV = new ConditionalValue<>(true, null);
			TypeToken<ConditionalValue<T>> cvType = (TypeToken<ConditionalValue<T>>) (TypeToken<?>) TypeTokens.get()
				.of(ConditionalValue.class);
			ObservableValue<ConditionalValue<T>> conditionalValue;
			if (values.length == 0)
				conditionalValue = ObservableValue.of(cvType, defaultCV);
			else if (values.length == 1)
				conditionalValue = values[0].map(LambdaUtils.printableFn(cv -> cv.pass ? cv : defaultCV, "passOrNull", null));
			else
				conditionalValue = ObservableValue.firstValue(cvType, //
					LambdaUtils.printablePred(cv -> cv.pass, "pass", null), //
					LambdaUtils.printableSupplier(() -> defaultCV, () -> "null", null), values);
			return ObservableValue.flatten(conditionalValue.map(LambdaUtils.printableFn(cv -> cv.value, "value", null)));
		}

		private static class ConditionalValue<T> {
			final boolean pass;
			final ObservableValue<? extends T> value;

			ConditionalValue(boolean pass, ObservableValue<? extends T> value) {
				this.pass = pass;
				this.value = value;
			}

			@Override
			public String toString() {
				return value == null ? "StyleDefault" : value.toString();
			}
		}

		@Override
		public String toString() {
			return theAttribute.toString();
		}
	}
}
