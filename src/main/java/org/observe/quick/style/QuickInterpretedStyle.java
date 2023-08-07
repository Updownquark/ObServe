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
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigElement;

import com.google.common.reflect.TypeToken;

/** Represents style information for a {@link QonfigElement} */
public interface QuickInterpretedStyle {
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

	default <T> QuickElementStyleAttribute<T> get(String attributeName, TypeToken<T> type) throws IllegalArgumentException {
		return get(getAttribute(attributeName, type));
	}

	default <T> QuickElementStyleAttribute<T> get(String attributeName, Class<T> type) throws IllegalArgumentException {
		return get(attributeName, TypeTokens.get().of(type));
	}

	void update(InterpretedExpressoEnv env, QuickInterpretedStyleCache.Applications appCache) throws ExpressoInterpretationException;

	/** Default implementation */
	public class Default implements QuickInterpretedStyle {
		private final QuickCompiledStyle theDefinition;
		private final QuickInterpretedStyle theParent;
		private final List<InterpretedStyleValue<?>> theDeclaredValues;
		private final Map<QuickStyleAttribute<?>, QuickElementStyleAttribute<?>> theValues;
		private final BetterMultiMap<String, QuickStyleAttribute<?>> theAttributesByName;

		/**
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
		public void update(InterpretedExpressoEnv env, QuickInterpretedStyleCache.Applications appCache)
			throws ExpressoInterpretationException {
			theDeclaredValues.clear();
			for (QuickStyleValue value : getDefinition().getDeclaredValues())
				theDeclaredValues.add(value.interpret(env, appCache));
			QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
			for (QuickStyleAttributeDef attr : getDefinition().getAttributes()) {
				QuickStyleAttribute<Object> interpretedAttr = (QuickStyleAttribute<Object>) cache.getAttribute(attr, env);
				QuickInterpretedStyle.QuickElementStyleAttribute<Object> inherited;
				inherited = theParent != null && attr.isTrickleDown() ? getInherited(theParent, interpretedAttr) : null;
				theValues.put(interpretedAttr, getDefinition().getValues(attr).interpret(this, inherited, env, appCache));
			}
			theAttributesByName.clear();
			for (QuickStyleAttribute<?> attr : theValues.keySet())
				theAttributesByName.add(attr.getName(), attr);
		}

		private static <T> QuickElementStyleAttribute<T> getInherited(QuickInterpretedStyle parent, QuickStyleAttribute<T> attr) {
			while (parent != null && !parent.getAttributes().contains(attr))
				parent = parent.getParent();
			return parent == null ? null : parent.get(attr);
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
			return new QuickElementStyleAttribute<>(attr, this, Collections.emptyList(), //
				theParent != null && attr.getDefinition().isTrickleDown() ? theParent.get(attr) : null);
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
		public void update(InterpretedExpressoEnv env, QuickInterpretedStyleCache.Applications appCache)
			throws ExpressoInterpretationException {
			theWrapped.update(env, appCache);
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
		public QuickElementStyleAttribute(QuickStyleAttribute<T> attribute, QuickInterpretedStyle style,
			List<InterpretedStyleValue<T>> values, QuickElementStyleAttribute<T> inherited) {
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
				SettableValue<T> value = theValues.get(i).getValue().get(models);
				values[i] = condition.map(LambdaUtils.printableFn(pass -> new ConditionalValue<>(Boolean.TRUE.equals(pass), value),
					"ifPass(" + value + ")", null));
			}
			if (theInherited != null) {
				ObservableValue<T> value = theInherited.evaluate(InterpretedStyleApplication.getParentModels(models));
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
			TypeToken<ObservableValue<T>> ovType = TypeTokens.get().keyFor(ObservableValue.class)
				.<ObservableValue<T>> parameterized(theAttribute.getType());
			return ObservableValue.flatten(conditionalValue.map(ovType, LambdaUtils.printableFn(cv -> cv.value, "value", null)));
		}

		private static class ConditionalValue<T> {
			final boolean pass;
			final ObservableValue<T> value;

			ConditionalValue(boolean pass, ObservableValue<T> value) {
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
