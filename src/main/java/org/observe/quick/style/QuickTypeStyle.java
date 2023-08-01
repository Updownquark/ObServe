package org.observe.quick.style;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.observe.expresso.VariableType;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;
import org.qommons.io.ErrorReporting;
import org.qommons.io.LocatedPositionedContent;

/** Represents style information for a {@link QonfigElementOrAddOn} */
public class QuickTypeStyle {
	/** The name of the &lt;styled> add-on, which all elements that styles apply to inherits */
	public static final String STYLED = "styled";
	/** The name of the &lt;style-attribute> element, which defines a style attribute */
	public static final String STYLE_ATTRIBUTE = "style-attribute";

	/** A set of {@link QuickTypeStyle}s */
	public static class TypeStyleSet {
		private final Map<QonfigElementOrAddOn, QuickTypeStyle> theElementStyleTypes = new ConcurrentHashMap<>();

		/**
		 * @param element The element type to get the style type of
		 * @return The style type for the given element type, or null if it has not been
		 *         {@link #getOrCompile(QonfigElementOrAddOn, ErrorReporting, QonfigToolkit) compiled}
		 */
		public QuickTypeStyle get(QonfigElementOrAddOn element) {
			return theElementStyleTypes.get(element);
		}

		/**
		 * @param element The element type to get the style type of
		 * @param reporting The error reporting to use for errors to get the {@link ExpressoQIS#getExpressoEnv() expresso environment} from
		 *        and for {@link ErrorReporting#error(String) error reporting}
		 * @param style The toolkit inheriting Quick-Style
		 * @return The style type for the given element type
		 * @throws QonfigInterpretationException If an error occurs synthesizing the style information for the given element
		 */
		public QuickTypeStyle getOrCompile(QonfigElementOrAddOn element, ErrorReporting reporting, QonfigToolkit style)
			throws QonfigInterpretationException {
			QuickTypeStyle styled = get(element);
			if (styled != null)
				return styled;
			QonfigAddOn styledAddOn = style.getAddOn(STYLED);
			if (!styledAddOn.isAssignableFrom(element))
				return null;
			List<QuickTypeStyle> parents = new ArrayList<>();
			QonfigElementOrAddOn superEl = element.getSuperElement();
			if (superEl != null) {
				QuickTypeStyle parent = getOrCompile(superEl, reporting, style);
				if (parent != null)
					parents.add(parent);
			}
			for (QonfigAddOn inh : element.getInheritance()) {
				QuickTypeStyle parent = getOrCompile(inh, reporting, style);
				if (parent != null)
					parents.add(parent);
			}
			if (parents.isEmpty())
				parents = Collections.emptyList();
			else
				parents = Collections.unmodifiableList(parents);
			Map<String, QuickStyleAttributeDef> declaredAttributes = new LinkedHashMap<>();
			BetterMultiMap<String, QuickStyleAttributeDef> attributes = BetterHashMultiMap.<String, QuickStyleAttributeDef> build()
				.buildMultiMap();
			QonfigAttributeDef.Declared priorityAttr = getPriorityAttr(style);
			styled = new QuickTypeStyle(this, element, parents, priorityAttr, Collections.unmodifiableMap(declaredAttributes),
				BetterCollections.unmodifiableMultiMap(attributes));

			QonfigElementDef styleAttrEl = style.getElement(STYLE_ATTRIBUTE);
			QonfigAttributeDef.Declared nameAttr = styleAttrEl.getAttribute("name").getDeclared();
			QonfigAttributeDef.Declared typeAttr = styleAttrEl.getAttribute("type").getDeclared();
			QonfigAttributeDef.Declared trickleAttr = styleAttrEl.getAttribute("trickle-down").getDeclared();
			QonfigElement stylesEl = element.getMetadata().getRoot().getChildrenByRole()
				.get(styledAddOn.getMetaSpec().getChild("styles").getDeclared()).peekFirst();
			if (stylesEl != null) {
				for (QonfigElement styleAttr : stylesEl.getChildrenInRole(style, "styles", STYLE_ATTRIBUTE)) {
					String name = styleAttr.getAttributeText(nameAttr);
					if (declaredAttributes.containsKey(name)) {
						reporting.error("Multiple style attributes named '" + name + "' declared");
						continue;
					}
					QonfigValue typeV = styleAttr.getAttributes().get(typeAttr);
					VariableType type = VariableType.parseType(new LocatedPositionedContent.Default(typeV.fileLocation, typeV.position));
					declaredAttributes.put(name, new QuickStyleAttributeDef(styled, name, type, //
						styleAttr.getAttribute(trickleAttr, boolean.class), styleAttr.getDescription()));
				}
			}
			attributes.putAll(declaredAttributes);

			for (QuickTypeStyle parent : parents)
				attributes.putAll(parent.getAttributes());
			theElementStyleTypes.put(element, styled);
			return styled;
		}
	}

	/**
	 * @param style The toolkit inheriting Quick-Style
	 * @return The style-model-value.priority attribute from the Quick-Style toolkit, defining the priority of a model value in a style
	 *         condition
	 */
	public static QonfigAttributeDef.Declared getPriorityAttr(QonfigToolkit style) {
		return style.getAttribute("style-model-value", "priority").getDeclared();
	}

	private final TypeStyleSet theStyleTypes;
	private final QonfigElementOrAddOn theElement;
	private final List<QuickTypeStyle> theSuperElements;
	private final Map<String, QuickStyleAttributeDef> theDeclaredAttributes;
	private final BetterMultiMap<String, QuickStyleAttributeDef> theAttributes;
	private final QonfigAttributeDef.Declared thePriorityAttr;

	QuickTypeStyle(TypeStyleSet styleTypes, QonfigElementOrAddOn element, List<QuickTypeStyle> superElements,
		QonfigAttributeDef.Declared priorityAttr, //
		Map<String, QuickStyleAttributeDef> declaredAttributes, BetterMultiMap<String, QuickStyleAttributeDef> attributes) {
		theStyleTypes = styleTypes;
		theElement = element;
		theSuperElements = superElements;
		thePriorityAttr = priorityAttr;
		theDeclaredAttributes = declaredAttributes;
		theAttributes = attributes;
	}

	/** @return The element type this style type is for */
	public QonfigElementOrAddOn getElement() {
		return theElement;
	}

	/**
	 * @return Style information for all &lt;styled> element types that this type
	 *         {@link QonfigElementOrAddOn#isAssignableFrom(QonfigElementOrAddOn) extends/inherits}
	 */
	public List<QuickTypeStyle> getSuperElements() {
		return theSuperElements;
	}

	/**
	 * @return The style-model-value.priority attribute from the Quick-Style toolkit, defining the priority of a model value in a style
	 *         condition
	 */
	public QonfigAttributeDef.Declared getPriorityAttr() {
		return thePriorityAttr;
	}

	/** @return All style attributes declared for this type specifically, by name */
	public Map<String, QuickStyleAttributeDef> getDeclaredAttributes() {
		return theDeclaredAttributes;
	}

	/** @return All style attributes declared for this type and all its {@link #getSuperElements() super types} */
	public BetterMultiMap<String, QuickStyleAttributeDef> getAttributes() {
		return theAttributes;
	}

	/**
	 * @param name The name of the attribute (may be qualified by element type)
	 * @return The attribute referred to by the given name
	 * @throws IllegalArgumentException If no such attribute could be found, or if multiple attributes match the given name
	 */
	public QuickStyleAttributeDef getAttribute(String name) throws IllegalArgumentException {
		int dot = name.indexOf('.');
		if (dot < 0) {
			QuickStyleAttributeDef attr = theDeclaredAttributes.get(name);
			if (attr != null)
				return attr;
			BetterCollection<QuickStyleAttributeDef> attrs = theAttributes.get(name);
			if (attrs.isEmpty())
				throw new IllegalArgumentException("No such style attribute: " + theElement + "." + name);
			else if (attrs.size() > 1)
				throw new IllegalArgumentException("Multiple attributes named '" + name + "' inherited by " + theElement);
			return attrs.getFirst();
		} else {
			String elName = name.substring(0, dot);
			QonfigElementOrAddOn el = theElement.getDeclarer().getElementOrAddOn(elName);
			if (el == null)
				throw new IllegalArgumentException("No such element or add-on '" + elName + "'");
			QuickTypeStyle styled = theStyleTypes.get(el);
			if (styled == null)
				throw new IllegalArgumentException(theElement + " is not related to " + elName);
			return styled.getAttribute(name.substring(dot + 1));
		}
	}

	/**
	 * @param name The name of the attribute(s) to get (may be qualified by element type)
	 * @return All attributes relevant to this type matching the given name
	 * @throws IllegalArgumentException If the qualified element type could not be found or is not related to this element
	 */
	public Collection<QuickStyleAttributeDef> getAttributes(String name) throws IllegalArgumentException {
		int dot = name.indexOf('.');
		if (dot < 0) {
			return theAttributes.get(name);
		} else {
			String elName = name.substring(0, dot);
			QonfigElementOrAddOn el = theElement.getDeclarer().getElementOrAddOn(elName);
			if (el == null)
				throw new IllegalArgumentException("No such element or add-on '" + elName + "'");
			QuickTypeStyle styled = theStyleTypes.get(el);
			if (styled == null)
				throw new IllegalArgumentException(theElement + " is not related to " + elName);
			return styled.getAttributes(name.substring(dot + 1));
		}
	}

	@Override
	public String toString() {
		return theElement.toString();
	}
}
