package org.observe.quick.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.style.QuickInterpretedStyle.QuickElementStyleAttribute;
import org.observe.quick.style.QuickTypeStyle.TypeStyleSet;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.collect.BetterSortedList;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;
import org.qommons.io.ErrorReporting;
import org.qommons.tree.SortedTreeList;

/** A compiled structure of all style values that may under any circumstance apply to a particular {@link QonfigElement element} */
public interface QuickCompiledStyle {
	/** @return The style set that this compiled style belongs to */
	QuickTypeStyle.TypeStyleSet getStyleTypes();

	/** @return This element's {@link QonfigElement#getParent() parent}'s style */
	QuickCompiledStyle getParent();

	/** @return The element this style is for */
	QonfigElement getElement();

	/** @return All style values that may apply to this style */
	List<QuickStyleValue> getDeclaredValues();

	/** @return All style attributes that apply to this element */
	Set<QuickStyleAttributeDef> getAttributes();

	/** @return A multi-map of all style values applicable to this style, keyed by name */
	BetterMultiMap<String, QuickStyleAttributeDef> getAttributesByName();

	/** @return All style attributes for which this style has values */
	Set<QuickStyleAttributeDef> getAttributesWithValues();

	QuickCompiledStyleAttribute getValues(QuickStyleAttributeDef attribute);

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
	default <T> QuickStyleAttributeDef getAttribute(String attributeName) throws IllegalArgumentException {
		BetterCollection<QuickStyleAttributeDef> attrs = getAttributesByName().get(attributeName);
		if (attrs.isEmpty())
			throw new IllegalArgumentException("No such attribute: '" + attributeName + "'");
		else if (attrs.size() > 1)
			throw new IllegalArgumentException("Multiple attributes named '" + attributeName + "': " + attrs);
		return attrs.iterator().next();
	}

	void update(List<QuickStyleValue> declaredValues, List<QuickStyleValue> otherValues, CompiledExpressoEnv env)
		throws QonfigInterpretationException;

	/**
	 * Interprets this compiled structure
	 *
	 * @param parent The interpreted style of this style's element's {@link QonfigElement#getParent() parent}
	 * @param applications A cache of interpreted style applications for re-use
	 * @return The interpreted style for this style's element
	 * @throws ExpressoInterpretationException If this structure's expressions could not be evaluated
	 */
	QuickInterpretedStyle interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
		throws ExpressoInterpretationException;

	/** Default {@link QuickCompiledStyle} implementation */
	public class Default implements QuickCompiledStyle {
		private final QuickTypeStyle.TypeStyleSet theStyleTypes;
		private final QonfigElement theElement;
		private final QuickCompiledStyle theParent;
		private final List<QuickStyleValue> theDeclaredValues;
		private final Map<QuickStyleAttributeDef, QuickCompiledStyleAttribute> theValues;
		private final Set<QuickStyleAttributeDef> theAttributes;
		private final BetterMultiMap<String, QuickStyleAttributeDef> theAttributesByName;

		/**
		 * @param styleTypes The style type set for this compiled style
		 * @param parent The element style for the {@link QonfigElement#getParent() parent} element
		 * @param element The element this style is for
		 * @param reporting The error reporting for errors
		 * @param style The toolkit inheriting Quick-Style
		 * @throws QonfigInterpretationException If an error occurs evaluating all the style information for the element
		 */
		public Default(QuickTypeStyle.TypeStyleSet styleTypes, QonfigElement element, QuickCompiledStyle parent, ErrorReporting reporting,
			QonfigToolkit style) throws QonfigInterpretationException {
			theStyleTypes = styleTypes;
			theElement = element;
			theParent = parent;
			theDeclaredValues = new ArrayList<>();

			theValues = new HashMap<>();

			BetterMultiMap<String, QuickStyleAttributeDef> attrsByName = BetterHashMultiMap.<String, QuickStyleAttributeDef> buildHashed()//
				.withDistinctValues().buildMultiMap();
			attrsByName.putAll(styleTypes.getOrCompile(element.getType(), reporting, style).getAttributes());
			for (QonfigElementOrAddOn inh : element.getInheritance().values()) {
				QuickTypeStyle inhStyle = styleTypes.getOrCompile(inh, reporting, style);
				if (inhStyle != null)
					attrsByName.putAll(inhStyle.getAttributes());
			}
			theAttributesByName = BetterCollections.unmodifiableMultiMap(attrsByName);
			Set<QuickStyleAttributeDef> attrs = new LinkedHashSet<>();
			attrs.addAll(theAttributesByName.values());
			theAttributes = Collections.unmodifiableSet(attrs);
		}

		@Override
		public void update(List<QuickStyleValue> declaredValues, List<QuickStyleValue> otherValues, CompiledExpressoEnv env)
			throws QonfigInterpretationException {
			theDeclaredValues.clear();
			theDeclaredValues.addAll(declaredValues);

			theValues.clear();
			Map<QuickStyleAttributeDef, BetterSortedList<QuickStyleValue>> values = new HashMap<>();
			for (QuickStyleValue sv : theDeclaredValues)
				values.computeIfAbsent(sv.getAttribute(), __ -> createStyleValueList()).add(sv);
			for (QuickStyleValue sv : otherValues)
				values.computeIfAbsent(sv.getAttribute(), __ -> createStyleValueList()).add(sv);
			if (theParent != null)
				addInheritedStyleValues(theParent, values, env.getModels());
			for (Map.Entry<QuickStyleAttributeDef, BetterSortedList<QuickStyleValue>> v : values.entrySet()) {
				QuickStyleAttributeDef attr = v.getKey();
				theValues.put(attr, new QuickCompiledStyleAttribute(attr, this, QommonsUtils.unmodifiableCopy(v.getValue())));
			}
		}

		private static BetterSortedList<QuickStyleValue> createStyleValueList() {
			return SortedTreeList.<QuickStyleValue> buildTreeList(QuickStyleValue::compareTo).build();
		}

		private void addInheritedStyleValues(QuickCompiledStyle parent,
			Map<QuickStyleAttributeDef, BetterSortedList<QuickStyleValue>> values, ObservableModelSet models) {
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
			/* I'm glad I only have to do this during the compile stage.
			 * We can't anticipate what style attributes will be specified, or who they'll apply to, so we have to climb all the way
			 * to the root looking for style attributes that could potentially apply to us.
			 */
			Set<QuickStyleAttributeDef> excludeAttrs = new HashSet<>();
			while (parent != null) {
				for (QuickStyleAttributeDef attr : parent.getAttributesWithValues()) {
					if (!excludeAttrs.contains(attr) && theAttributes.contains(attr)
						&& (attr.isTrickleDown() || !parent.getAttributes().contains(attr))) {
						for (QuickStyleValue value : parent.getValues(attr).getValues())
							values.computeIfAbsent(value.getAttribute(), __ -> createStyleValueList()).add(value.forInherited(models));
					}
				}
				for (QuickStyleAttributeDef attr : parent.getAttributes()) {
					if (!attr.isTrickleDown())
						excludeAttrs.add(attr);
				}

				parent = parent.getParent();
			}
		}

		@Override
		public QuickTypeStyle.TypeStyleSet getStyleTypes() {
			return theStyleTypes;
		}

		@Override
		public QuickCompiledStyle getParent() {
			return theParent;
		}

		@Override
		public QonfigElement getElement() {
			return theElement;
		}

		@Override
		public List<QuickStyleValue> getDeclaredValues() {
			return Collections.unmodifiableList(theDeclaredValues);
		}

		@Override
		public Set<QuickStyleAttributeDef> getAttributes() {
			return theAttributes;
		}

		@Override
		public BetterMultiMap<String, QuickStyleAttributeDef> getAttributesByName() {
			return BetterCollections.unmodifiableMultiMap(theAttributesByName);
		}

		@Override
		public Set<QuickStyleAttributeDef> getAttributesWithValues() {
			return Collections.unmodifiableSet(theValues.keySet());
		}

		@Override
		public QuickCompiledStyleAttribute getValues(QuickStyleAttributeDef attribute) {
			return theValues.get(attribute);
		}

		@Override
		public QuickInterpretedStyle interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
			throws ExpressoInterpretationException {
			return new QuickInterpretedStyle.Default(this, parent);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(theElement.getType().getName()).append(" style:");
			for (QuickStyleValue value : theDeclaredValues)
				str.append("\n\t").append(value);
			return str.toString();
		}
	}

	/** A wrapper around another compiled style */
	public abstract class Wrapper implements QuickCompiledStyle {
		private final QuickCompiledStyle theParent;
		private final QuickCompiledStyle theWrapped;

		/**
		 * @param parent The parent style
		 * @param wrapped The style to wrap
		 */
		protected Wrapper(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
			theParent = parent;
			theWrapped = wrapped;
		}

		/** @return The wrapped style */
		protected QuickCompiledStyle getWrapped() {
			return theWrapped;
		}

		@Override
		public TypeStyleSet getStyleTypes() {
			return theWrapped.getStyleTypes();
		}

		@Override
		public QuickCompiledStyle getParent() {
			return theParent;
		}

		@Override
		public QonfigElement getElement() {
			return theWrapped.getElement();
		}

		@Override
		public List<QuickStyleValue> getDeclaredValues() {
			return theWrapped.getDeclaredValues();
		}

		@Override
		public Set<QuickStyleAttributeDef> getAttributes() {
			return theWrapped.getAttributes();
		}

		@Override
		public BetterMultiMap<String, QuickStyleAttributeDef> getAttributesByName() {
			return theWrapped.getAttributesByName();
		}

		@Override
		public Set<QuickStyleAttributeDef> getAttributesWithValues() {
			return theWrapped.getAttributesWithValues();
		}

		@Override
		public QuickCompiledStyleAttribute getValues(QuickStyleAttributeDef attribute) {
			return theWrapped.getValues(attribute);
		}

		@Override
		public void update(List<QuickStyleValue> declaredValues, List<QuickStyleValue> otherValues, CompiledExpressoEnv env)
			throws QonfigInterpretationException {
			theWrapped.update(declaredValues, otherValues, env);
		}

		@Override
		public QuickInterpretedStyle interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
			throws ExpressoInterpretationException {
			return theWrapped.interpret(parentEl, parent, env);
		}

		@Override
		public String toString() {
			return theWrapped.toString();
		}
	}

	/** A structure containing all information necessary to get the value of a style attribute for an element */
	public static class QuickCompiledStyleAttribute {
		private final QuickStyleAttributeDef theAttribute;
		private final QuickCompiledStyle theStyle;
		private final List<QuickStyleValue> theValues;

		/**
		 * @param attribute The attribute this structure is for
		 * @param style The element style this structure is for
		 * @param values All style values that may apply to the element for the attribute
		 */
		public QuickCompiledStyleAttribute(QuickStyleAttributeDef attribute, QuickCompiledStyle style, List<QuickStyleValue> values) {
			theAttribute = attribute;
			theStyle = style;
			theValues = values;
		}

		/** @return The attribute this structure is for */
		public QuickStyleAttributeDef getAttribute() {
			return theAttribute;
		}

		/** @return The element style this structure is for */
		public QuickCompiledStyle getStyle() {
			return theStyle;
		}

		/** @return All style values that may apply to the element for the attribute */
		public List<QuickStyleValue> getValues() {
			return theValues;
		}

		/**
		 * @param elementStyle The element style to interpret this attribute value into
		 * @param env The expresso environment to use to evaluate the style values
		 * @param appCache The application cache for re-use of {@link InterpretedStyleApplication}s
		 * @return The interpreted value for this style attribute on the element
		 * @throws ExpressoInterpretationException If the condition or the value could not be interpreted
		 */
		public <T> QuickElementStyleAttribute<T> interpret(QuickInterpretedStyle elementStyle, InterpretedExpressoEnv env,
			QuickStyleSheet.Interpreted styleSheet, QuickInterpretedStyleCache.Applications appCache)
				throws ExpressoInterpretationException {
			QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
			QuickStyleAttribute<T> attribute = (QuickStyleAttribute<T>) cache.getAttribute(theAttribute, env);
			List<InterpretedStyleValue<T>> values = new ArrayList<>(theValues.size());
			for (QuickStyleValue v : theValues)
				values.add((InterpretedStyleValue<T>) v.interpret(env, styleSheet, appCache));
			return new QuickElementStyleAttribute<>(attribute, elementStyle, Collections.unmodifiableList(values));
		}

		@Override
		public String toString() {
			return theStyle.getElement() + "." + theAttribute;
		}
	}
}
