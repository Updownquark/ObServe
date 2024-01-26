package org.observe.quick.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.InterpretedStyleValue.StyleValueInstantiator;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigElement;

import com.google.common.reflect.TypeToken;

/** Represents style information for a {@link QonfigElement} */
public interface QuickInterpretedStyle {
	/** @return This interpreted style's definition */
	QuickCompiledStyle getDefinition();

	/** @return This element's {@link QonfigElement#getParent() parent}'s style */
	QuickInterpretedStyle getParent();

	/** @return All style values that may apply to this style */
	List<InterpretedStyleValue<?>> getDeclaredValues();

	/** @return All style attributes that apply to this element */
	Set<QuickStyleAttribute<?>> getAttributes();

	/** @return A multi-map of all style values applicable to this style, keyed by name */
	BetterMultiMap<String, QuickStyleAttribute<?>> getAttributesByName();

	/**
	 * Gets a style attribute in this style by name
	 *
	 * @param <T> The type of the style attribute to get
	 * @param attributeName The name of the style attribute to get
	 * @param type The type of the style attribute to get
	 * @return The style attribute in this style with the given name
	 * @throws IllegalArgumentException If there is no such attribute with the given name applicable to this style's element, there are
	 *         multiple such styles, or the applicable style's {@link QuickStyleAttributeDef#getType() type} is not the same as that given
	 */
	default <T> QuickStyleAttribute<T> getAttribute(String attributeName, TypeToken<T> type) throws IllegalArgumentException {
		BetterCollection<QuickStyleAttribute<?>> attrs = getAttributesByName().get(attributeName);
		if (attrs.isEmpty())
			throw new IllegalArgumentException("No such attribute: '" + attributeName + "'");
		else if (attrs.size() > 1)
			throw new IllegalArgumentException("Multiple attributes named '" + attributeName + "': " + attrs);
		QuickStyleAttribute<?> attr = attrs.iterator().next();
		if (!type.equals(attr.getType()) && !type.unwrap().equals(attr.getType().unwrap()))
			throw new IllegalArgumentException("Attribute " + attr.getDefinition().getDeclarer().getElement().getName() + "."
				+ attr.getName() + " is typed " + attr.getType() + ", not " + type);
		return (QuickStyleAttribute<T>) attr;
	}

	/**
	 * Gets a style attribute in this style by name
	 *
	 * @param <T> The type of the style attribute to get
	 * @param attributeName The name of the style attribute to get
	 * @param type The type of the style attribute to get
	 * @return The style attribute in this style with the given name
	 * @throws IllegalArgumentException If there is no such attribute with the given name applicable to this style's element, there are
	 *         multiple such styles, or the applicable style's {@link QuickStyleAttributeDef#getType() type} is not the same as that given
	 */
	default <T> QuickStyleAttribute<T> getAttribute(String attributeName, Class<T> type) {
		return getAttribute(attributeName, TypeTokens.get().of(type));
	}

	/**
	 * @param <T> The type of the attribute
	 * @param attr The attribute to get the value for
	 * @return A structure containing all information necessary to get the value of a style attribute for this element
	 */
	<T> QuickElementStyleAttribute<T> get(QuickStyleAttribute<T> attr);

	/**
	 * @param <T> The type of the attribute
	 * @param attributeName The name of the attribute
	 * @param type The type of the attribute
	 * @return The interpreted attribute style for the attribute
	 * @throws IllegalArgumentException If the attribute could not be resolved or multiple attributes with the given name were found
	 */
	default <T> QuickElementStyleAttribute<T> get(String attributeName, TypeToken<T> type) throws IllegalArgumentException {
		return get(getAttribute(attributeName, type));
	}

	/**
	 * @param <T> The type of the attribute
	 * @param attributeName The name of the attribute
	 * @param type The type of the attribute
	 * @return The interpreted attribute style for the attribute
	 * @throws IllegalArgumentException If the attribute could not be resolved or multiple attributes with the given name were found
	 */
	default <T> QuickElementStyleAttribute<T> get(String attributeName, Class<T> type) throws IllegalArgumentException {
		return get(attributeName, TypeTokens.get().of(type));
	}

	/**
	 * Initializes or updates this style
	 *
	 * @param env The expresso environment to interpret expressions with
	 * @param styleSheet The application style sheet
	 * @param appCache The application cache
	 * @throws ExpressoInterpretationException If this style could not be interpreted
	 */
	void update(InterpretedExpressoEnv env, QuickStyleSheet.Interpreted styleSheet, QuickInterpretedStyleCache.Applications appCache)
		throws ExpressoInterpretationException;

	/** Default implementation */
	public class Default implements QuickInterpretedStyle {
		private final QuickCompiledStyle theDefinition;
		private final QuickInterpretedStyle theParent;
		private final List<InterpretedStyleValue<?>> theDeclaredValues;
		private final Map<QuickStyleAttribute<?>, QuickElementStyleAttribute<?>> theValues;
		private final BetterMultiMap<String, QuickStyleAttribute<?>> theAttributesByName;

		/**
		 * @param definition The definition to interpret
		 * @param parent The element style for the {@link QonfigElement#getParent() parent} element
		 */
		public Default(QuickCompiledStyle definition, QuickInterpretedStyle parent) {
			theDefinition = definition;
			theParent = parent;
			theDeclaredValues = new ArrayList<>();
			theValues = new HashMap<>();
			theAttributesByName = BetterHashMultiMap.<String, QuickStyleAttribute<?>> buildHashed()//
				.withDistinctValues().buildMultiMap();
		}

		@Override
		public void update(InterpretedExpressoEnv env, QuickStyleSheet.Interpreted styleSheet,
			QuickInterpretedStyleCache.Applications appCache) throws ExpressoInterpretationException {
			theDeclaredValues.clear();
			for (QuickStyleValue value : getDefinition().getDeclaredValues())
				theDeclaredValues.add(value.interpret(env, styleSheet, appCache));
			QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
			for (QuickStyleAttributeDef attr : getDefinition().getAttributesWithValues()) {
				QuickStyleAttribute<Object> interpretedAttr = (QuickStyleAttribute<Object>) cache.getAttribute(attr, env);
				theValues.put(interpretedAttr, getDefinition().getValues(attr).interpret(this, env, styleSheet, appCache));
			}
			theAttributesByName.clear();
			for (QuickStyleAttribute<?> attr : theValues.keySet())
				theAttributesByName.add(attr.getName(), attr);
		}

		@Override
		public QuickCompiledStyle getDefinition() {
			return theDefinition;
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
		public BetterMultiMap<String, QuickStyleAttribute<?>> getAttributesByName() {
			return theAttributesByName;
		}

		@Override
		public <T> QuickElementStyleAttribute<T> get(QuickStyleAttribute<T> attr) {
			QuickElementStyleAttribute<T> value = (QuickElementStyleAttribute<T>) theValues.get(attr);
			if (value != null)
				return value;
			return new QuickElementStyleAttribute<>(attr, this, Collections.emptyList());
		}

		@Override
		public String toString() {
			return theDefinition.toString();
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
		public BetterMultiMap<String, QuickStyleAttribute<?>> getAttributesByName() {
			return theWrapped.getAttributesByName();
		}

		@Override
		public <T> QuickElementStyleAttribute<T> get(QuickStyleAttribute<T> attr) {
			return theWrapped.get(attr);
		}

		@Override
		public void update(InterpretedExpressoEnv env, QuickStyleSheet.Interpreted styleSheet,
			QuickInterpretedStyleCache.Applications appCache) throws ExpressoInterpretationException {
			theWrapped.update(env, styleSheet, appCache);
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

		/**
		 * @param attribute The attribute this structure is for
		 * @param style The element style this structure is for
		 * @param values All style values that may apply to the element for the attribute
		 */
		public QuickElementStyleAttribute(QuickStyleAttribute<T> attribute, QuickInterpretedStyle style,
			List<InterpretedStyleValue<T>> values) {
			theAttribute = attribute;
			theStyle = style;
			theValues = values;
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

		/** @return All style values in this interpreted style attribute */
		public List<BiTuple<QuickInterpretedStyle, InterpretedStyleValue<T>>> getAllValues() {
			List<BiTuple<QuickInterpretedStyle, InterpretedStyleValue<T>>> values = new ArrayList<>();
			for (InterpretedStyleValue<T> value : theValues)
				values.add(new BiTuple<>(theStyle, value));
			return values;
		}

		/**
		 * @param models The interpreted models to instantiate for
		 * @return An instantiator for style values for this attribute
		 */
		public QuickStyleAttributeInstantiator<T> instantiate(InterpretedModelSet models) {
			return new QuickStyleAttributeInstantiator<>(theAttribute,
				QommonsUtils.map(theValues, v -> v.instantiate(models), true));
		}

		@Override
		public String toString() {
			return theAttribute.toString();
		}
	}

	/**
	 * Instantiator for Quick style values for an attribute
	 *
	 * @param <T> The type of the style attribute this instantiator is for
	 */
	public static class QuickStyleAttributeInstantiator<T> {
		private final QuickStyleAttribute<T> theAttribute;
		private final List<InterpretedStyleValue.StyleValueInstantiator<T>> theValues;

		/**
		 * @param attribute The attribute this instantiator is for
		 * @param values The list of style values to instantiate
		 */
		public QuickStyleAttributeInstantiator(QuickStyleAttribute<T> attribute, List<StyleValueInstantiator<T>> values) {
			theAttribute = attribute;
			theValues = values;
		}

		/** @return The attribute this instantiator is for */
		public QuickStyleAttribute<T> getAttribute() {
			return theAttribute;
		}

		/** @return The style values determining the value of the style */
		public List<InterpretedStyleValue.StyleValueInstantiator<T>> getValues() {
			return theValues;
		}

		/** Instantiates model values in this instantiator. Must be called once after creation. */
		public void instantiate() {
			for (InterpretedStyleValue.StyleValueInstantiator<T> value : theValues)
				value.instantiate();
		}

		/**
		 * @param models The models to use to instantiate the style values
		 * @return All conditional values for the style attribute
		 * @throws ModelInstantiationException If the style values could not be instantiated
		 */
		public List<ObservableValue<ConditionalValue<T>>> getConditionalValues(ModelSetInstance models) throws ModelInstantiationException {
			List<ObservableValue<ConditionalValue<T>>> values = new ArrayList<>();
			for (int i = 0; i < theValues.size(); i++) {
				InterpretedStyleValue.StyleValueInstantiator<T> styleValue = theValues.get(i);
				if (styleValue.modelContext != null)
					styleValue.modelContext.populateModel(models);
				ObservableValue<Boolean> condition = styleValue.condition.get(models);
				SettableValue<T> value = styleValue.value.get(models);
				values.add(condition.map(LambdaUtils.printableFn(pass -> new ConditionalValue<>(Boolean.TRUE.equals(pass), value),
					"ifPass(" + value + ")", null)));
			}
			return values;
		}

		/**
		 * @param models The model instance to get the value for
		 * @return The value for this style attribute on the element
		 * @throws ModelInstantiationException If the condition or the value could not be evaluated
		 */
		public ObservableValue<T> evaluate(ModelSetInstance models) throws ModelInstantiationException {
			List<ObservableValue<ConditionalValue<T>>> valueList = getConditionalValues(models);
			if (valueList.isEmpty())
				return ObservableValue.of(theAttribute.getType(), null);
			TypeToken<ObservableValue<T>> ovType = TypeTokens.get().keyFor(ObservableValue.class)
				.<ObservableValue<T>> parameterized(theAttribute.getType());
			ObservableValue<ConditionalValue<T>> conditionalValue;
			if (valueList.size() == 1)
				conditionalValue = valueList.get(0);
			else {
				ObservableValue<ConditionalValue<T>>[] values = valueList.toArray(new ObservableValue[valueList.size()]);
				TypeToken<ConditionalValue<T>> cvType = TypeTokens.get().keyFor(ConditionalValue.class)
					.<ConditionalValue<T>> parameterized(theAttribute.getType());
				conditionalValue = ObservableValue.firstValue(cvType, //
					LambdaUtils.printablePred(cv -> cv.pass, "pass", null), LambdaUtils.constantSupplier(null, "null", null), values);
			}
			return ObservableValue.flatten(
				conditionalValue.map(ovType, LambdaUtils.printableFn(cv -> (cv != null && cv.pass) ? cv.value : null, "value", null)));
		}
	}

	/**
	 * A tuple containing the observable value of a style value and whether its condition is currently passing
	 *
	 * @param <T> The type of the attribute
	 */
	public static class ConditionalValue<T> {
		/** Whether the style value's condition is passing */
		public final boolean pass;
		/** The value for the style */
		public final ObservableValue<T> value;

		ConditionalValue(boolean pass, ObservableValue<T> value) {
			this.pass = pass;
			this.value = value;
		}

		@Override
		public String toString() {
			return (pass ? "*" : "x") + (value == null ? "StyleDefault" : value.toString());
		}
	}
}
