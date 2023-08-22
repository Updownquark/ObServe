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
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
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
import org.qommons.config.QonfigElementOrAddOn;

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
				inherited = getInherited(theParent, interpretedAttr);
				theValues.put(interpretedAttr, getDefinition().getValues(attr).interpret(this, inherited, env, appCache));
			}
			theAttributesByName.clear();
			for (QuickStyleAttribute<?> attr : theValues.keySet())
				theAttributesByName.add(attr.getName(), attr);
		}

		private <T> QuickElementStyleAttribute<T> getInherited(QuickInterpretedStyle parent, QuickStyleAttribute<T> attr) {
			if (parent == null)
				return null;

			/* This part is a little confusing, but hear me out:
			 * If the attribute is marked trickle down, then it should inherit from the nearest ancestor for which the attribute has a value.
			 *
			 * So far, so good.
			 *
			 * But there is another more subtle case where an attribute that is *not* marked trickle-down should inherit from an ancestor.
			 * If a value for the attribute is defined on an ancestor to which the attribute doesn't actually apply,
			 * this is obviously intended to apply to the descendants of the element, since otherwise the value could never apply to anything.
			 *
			 * An example use case of this is to set the widget color for a column.  Column has no styles itself, but it inherits styled
			 * explicitly to support targeting with styles which will be used by its descendants, e.g. the renderer and editor.
			 */
			QonfigElementOrAddOn testType;
			if (attr.getDefinition().isTrickleDown())
				testType = null; // Trickle down, so there's no test--always inherit
			else
				testType = attr.getDefinition().getDeclarer().getElement();
			while (parent != null) {
				if (testType != null && parent.getDefinition().getElement().isInstance(testType))
					return null;
				else if (parent.getAttributes().contains(attr))
					break;
				parent = parent.getParent();
			}
			if (parent == null)
				return null;
			// We have an ancestor for which the attribute has a value
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
			return new QuickElementStyleAttribute<>(attr, this, Collections.emptyList(), getInherited(theParent, attr));
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

		public List<BiTuple<QuickInterpretedStyle, InterpretedStyleValue<T>>> getAllValues() {
			List<BiTuple<QuickInterpretedStyle, InterpretedStyleValue<T>>> values = new ArrayList<>();
			for (InterpretedStyleValue<T> value : theValues)
				values.add(new BiTuple<>(theStyle, value));
			if (theInherited != null)
				values.addAll(theInherited.getAllValues());
			return values;
		}

		public QuickStyleAttributeInstantiator<T> instantiate(InterpretedModelSet models) {
			QuickStyleAttributeInstantiator<T> inherited;
			ModelComponentId parentModelValue;
			if (theInherited != null) {
				try {
					parentModelValue = models.getComponent(InterpretedStyleApplication.PARENT_MODEL_NAME).getIdentity();
				} catch (ModelException e) {
					throw new IllegalStateException("No parent model installed", e);
				}
				inherited = theInherited.instantiate(StyleApplicationDef.getParentModel(models));
			} else {
				inherited = null;
				parentModelValue = null;
			}
			return new QuickStyleAttributeInstantiator<>(theAttribute, QommonsUtils.map(theValues, v -> v.instantiate(models), true),
				inherited, parentModelValue);
		}

		@Override
		public String toString() {
			return theAttribute.toString();
		}
	}

	public static class QuickStyleAttributeInstantiator<T> {
		private final QuickStyleAttribute<T> theAttribute;
		private final List<InterpretedStyleValue.StyleValueInstantiator<T>> theValues;
		private final QuickStyleAttributeInstantiator<T> theInherited;
		private final ModelComponentId theParentModelValue;

		public QuickStyleAttributeInstantiator(QuickStyleAttribute<T> attribute, List<StyleValueInstantiator<T>> values,
			QuickStyleAttributeInstantiator<T> inherited, ModelComponentId parentModelValue) {
			theAttribute = attribute;
			theValues = values;
			theInherited = inherited;
			theParentModelValue = parentModelValue;
		}

		public QuickStyleAttribute<T> getAttribute() {
			return theAttribute;
		}

		public List<InterpretedStyleValue.StyleValueInstantiator<T>> getValues() {
			return theValues;
		}

		public QuickStyleAttributeInstantiator<T> getInherited() {
			return theInherited;
		}

		public void instantiate() {
			for (InterpretedStyleValue.StyleValueInstantiator<T> value : theValues)
				value.instantiate();
			if (theInherited != null)
				theInherited.instantiate();
		}

		public List<ObservableValue<ConditionalValue<T>>> getConditionalValues(ModelSetInstance models) throws ModelInstantiationException {
			List<ObservableValue<ConditionalValue<T>>> values = new ArrayList<>();
			for (int i = 0; i < theValues.size(); i++) {
				ObservableValue<Boolean> condition = theValues.get(i).condition.get(models);
				SettableValue<T> value = theValues.get(i).value.get(models);
				values.add(condition.map(LambdaUtils.printableFn(pass -> new ConditionalValue<>(Boolean.TRUE.equals(pass), value),
					"ifPass(" + value + ")", null)));
			}
			if (theInherited != null)
				values.addAll(theInherited.getConditionalValues(InterpretedStyleApplication.getParentModels(models, theParentModelValue)));
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

	public static class ConditionalValue<T> {
		public final boolean pass;
		public final ObservableValue<T> value;

		ConditionalValue(boolean pass, ObservableValue<T> value) {
			this.pass = pass;
			this.value = value;
		}

		@Override
		public String toString() {
			return value == null ? "StyleDefault" : value.toString();
		}
	}
}
