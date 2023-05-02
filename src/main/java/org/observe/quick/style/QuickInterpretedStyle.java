package org.observe.quick.style;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.collect.BetterCollection;
import org.qommons.config.QonfigElement;

import com.google.common.reflect.TypeToken;

/** Represents style information for a {@link QonfigElement} */
public class QuickInterpretedStyle {
	private final QuickInterpretedStyle theParent;
	private final QuickCompiledStyle theCompiledStyle;
	private final List<InterpretedStyleValue<?>> theDeclaredValues;
	private final Map<QuickStyleAttribute<?>, QuickElementStyleAttribute<?>> theValues;

	/**
	 *
	 * @param parent The element style for the {@link QonfigElement#getParent() parent} element
	 * @param compiled The compiled style that {@link QuickCompiledStyle#interpret(QuickInterpretedStyle, Map) interpreted} this style
	 * @param declaredValues All style values declared specifically on this element
	 * @param values All values for style attributes that vary on this style
	 */
	public QuickInterpretedStyle(QuickInterpretedStyle parent, QuickCompiledStyle compiled, List<InterpretedStyleValue<?>> declaredValues,
		Map<QuickStyleAttribute<?>, QuickElementStyleAttribute<?>> values) {
		theParent = parent;
		theCompiledStyle = compiled;
		theDeclaredValues = declaredValues;
		theValues = values;
	}

	/** @return This element's {@link QonfigElement#getParent() parent}'s style */
	public QuickInterpretedStyle getParent() {
		return theParent;
	}

	/** @return The compiled style that {@link QuickCompiledStyle#interpret(QuickInterpretedStyle, Map) interpreted} this style */
	public QuickCompiledStyle getCompiled() {
		return theCompiledStyle;
	}

	/** @return All style values that may apply to this style */
	public List<InterpretedStyleValue<?>> getDeclaredValues() {
		return theDeclaredValues;
	}

	/** @return All style attributes that apply to this element */
	public Set<QuickStyleAttribute<?>> getAttributes() {
		return theValues.keySet();
	}

	/**
	 * @param <T> The type of the attribute
	 * @param attr The attribute to get the value for
	 * @return A structure containing all information necessary to get the value of a style attribute for this element
	 */
	public <T> QuickElementStyleAttribute<T> get(QuickStyleAttribute<T> attr) {
		QuickElementStyleAttribute<T> value = (QuickElementStyleAttribute<T>) theValues.get(attr);
		if (value != null)
			return value;
		else if (!theCompiledStyle.getElement().isInstance(attr.getDeclarer().getElement()))
			throw new IllegalArgumentException(
				"Attribute " + attr + " is not valid for this element (" + theCompiledStyle.getElement().getType().getName() + ")");
		return new QuickElementStyleAttribute<>(attr, this, Collections.emptyList(), //
			theParent != null && attr.isTrickleDown() ? theParent.get(attr) : null);
	}

	/**
	 * Gets a style value in this style by name
	 *
	 * @param <T> The type of the style attribute to get
	 * @param attributeName The name of the style attribute to get
	 * @param type The type of the style attribute to get
	 * @return The style value in this style with the given name
	 * @throws IllegalArgumentException If there is no such attribute with the given name applicable to this style's element, there are
	 *         multiple such styles, or the applicable style's {@link QuickStyleAttribute#getType() type} is not the same as that given
	 */
	public <T> QuickElementStyleAttribute<T> get(String attributeName, TypeToken<T> type) throws IllegalArgumentException {
		BetterCollection<QuickStyleAttribute<?>> attrs = theCompiledStyle.getAttributesByName().get(attributeName);
		if (attrs.isEmpty())
			throw new IllegalArgumentException("No such attribute: '" + attributeName + "'");
		else if (attrs.size() > 1)
			throw new IllegalArgumentException("Multiple attributes named '" + attributeName + "': " + attrs);
		QuickStyleAttribute<?> attr = attrs.iterator().next();
		if (!type.equals(attr.getType()) && !type.unwrap().equals(attr.getType().unwrap()))
			throw new IllegalArgumentException("Attribute " + attr.getDeclarer().getElement().getName() + "." + attr.getName()
			+ " is typed " + attr.getType() + ", not " + type);
		return get((QuickStyleAttribute<T>) attr);
	}

	/**
	 * Gets a style value in this style by name
	 *
	 * @param <T> The type of the style attribute to get
	 * @param attributeName The name of the style attribute to get
	 * @param type The type of the style attribute to get
	 * @return The style value in this style with the given name
	 * @throws IllegalArgumentException If there is no such attribute with the given name applicable to this style's element, there are
	 *         multiple such styles, or the applicable style's {@link QuickStyleAttribute#getType() type} is not the same as that given
	 */
	public <T> QuickElementStyleAttribute<T> get(String attributeName, Class<T> type) {
		return get(attributeName, TypeTokens.get().of(type));
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
		 * @param values All style values that may apply to the element for teh attribute
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
		public ObservableValue<T> evaluate(ModelSetInstance models, T defaultValue) throws ModelInstantiationException {
			ObservableValue<ConditionalValue<T>>[] values = new ObservableValue[theValues.size() + (theInherited == null ? 0 : 1)];
			for (int i = 0; i < theValues.size(); i++) {
				ObservableValue<Boolean> condition = theValues.get(i).getApplication().getCondition(models);
				SettableValue<? extends T> value = theValues.get(i).getValue().get(models);
				values[i] = condition
					.map(LambdaUtils.printableFn(pass -> new ConditionalValue<>(pass, value), "ifPass(" + value + ")", null));
			}
			if (theInherited != null) {
				ObservableValue<T> value = theInherited.evaluate(StyleQIS.getParentModels(models), null);
				values[theValues.size()] = ObservableValue.of(new ConditionalValue<>(true, value));
			}
			ConditionalValue<T> defaultCV = new ConditionalValue<>(true, null);
			ObservableValue<ConditionalValue<T>> conditionalValue = ObservableValue.firstValue(
				(TypeToken<ConditionalValue<T>>) (TypeToken<?>) TypeTokens.get().of(ConditionalValue.class), //
				LambdaUtils.printablePred(cv -> cv.pass, "pass", null), //
				LambdaUtils.printableSupplier(() -> defaultCV, () -> "null", null), values);
			ObservableValue<T> value = ObservableValue
				.flatten(conditionalValue.map(LambdaUtils.printableFn(cv -> cv.value, "value", null)), () -> defaultValue);
			return value;
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
				return value.toString();
			}
		}

		@Override
		public String toString() {
			return theStyle.getCompiled().getElement() + "." + theAttribute;
		}
	}
}
