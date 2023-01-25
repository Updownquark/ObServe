package org.observe.quick.style;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.VariableType;
import org.observe.util.TypeTokens;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterHashMultiMap;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;

import com.google.common.reflect.TypeToken;

/** Represents style information for a {@link QonfigElementOrAddOn} */
public class QuickStyleType {
	/** The name of the &lt;styled> add-on, which all elements that styles apply to inherits */
	public static final String STYLED = "styled";
	/** The name of the &lt;style-attribute> element, which defines a style attribute */
	public static final String STYLE_ATTRIBUTE = "style-attribute";
	private static final IdentityHashMap<QonfigElementOrAddOn, QuickStyleType> ELEMENT_STYLE_TYPES = new IdentityHashMap<>();

	/**
	 * @param element The element type to get the style type of
	 * @param session The session to get the {@link ExpressoQIS#getExpressoEnv() expresso environment} from and for
	 *        {@link AbstractQIS#error(String) error reporting}
	 * @param style The toolkit inheriting Quick-Style
	 * @return The style type for the given element type
	 * @throws QonfigInterpretationException If an error occurs synthesizing the style information for the given element
	 */
	public static synchronized QuickStyleType of(QonfigElementOrAddOn element, AbstractQIS<?> session, QonfigToolkit style)
		throws QonfigInterpretationException {
		QuickStyleType styled = ELEMENT_STYLE_TYPES.get(element);
		if (styled != null)
			return styled;
		else if (session == null || style == null)
			return null;
		QonfigAddOn styledAddOn = style.getAddOn(STYLED);
		if (!styledAddOn.isAssignableFrom(element))
			return null;
		List<QuickStyleType> parents = new ArrayList<>();
		QonfigElementOrAddOn superEl = element.getSuperElement();
		if (superEl != null) {
			QuickStyleType parent = of(superEl, session, style);
			if (parent != null)
				parents.add(parent);
		}
		for (QonfigAddOn inh : element.getInheritance()) {
			QuickStyleType parent = of(inh, session, style);
			if (parent != null)
				parents.add(parent);
		}
		if (parents.isEmpty())
			parents = Collections.emptyList();
		else
			parents = Collections.unmodifiableList(parents);
		Map<String, QuickStyleAttribute<?>> declaredAttributes = new LinkedHashMap<>();
		BetterMultiMap<String, QuickStyleAttribute<?>> attributes = BetterHashMultiMap.<String, QuickStyleAttribute<?>> build()
			.buildMultiMap();
		QonfigAttributeDef.Declared priorityAttr = getPriorityAttr(style);
		styled = new QuickStyleType(element, parents, priorityAttr, Collections.unmodifiableMap(declaredAttributes),
			BetterCollections.unmodifiableMultiMap(attributes));

		QonfigElementDef styleAttrEl = style.getElement(STYLE_ATTRIBUTE);
		QonfigAttributeDef.Declared nameAttr = styleAttrEl.getAttribute("name").getDeclared();
		QonfigAttributeDef.Declared typeAttr = styleAttrEl.getAttribute("type").getDeclared();
		QonfigAttributeDef.Declared trickleAttr = styleAttrEl.getAttribute("trickle-down").getDeclared();
		QonfigElement stylesEl = element.getMetadata().getRoot().getChildrenByRole()
			.get(styledAddOn.getMetaSpec().getChild("styles").getDeclared()).peekFirst();
		if (stylesEl != null) {
			for (QonfigElement styleAttr : stylesEl.getChildrenInRole(style, "styles", "style-attribute")) {
				String name = styleAttr.getAttributeText(nameAttr);
				if (declaredAttributes.containsKey(name)) {
					session.error("Multiple style attributes named '" + name + "' declared");
					continue;
				}
				QonfigValue typeV = styleAttr.getAttributes().get(typeAttr);
				ExpressoEnv env = session.as(ExpressoQIS.class).getExpressoEnv();
				VariableType vblType = VariableType.parseType(typeV.text, env.getClassView(), typeV.fileLocation, typeV.position);
				TypeToken<?> type = vblType.getType(env.getModels());
				declaredAttributes.put(name, new QuickStyleAttribute<>(styled, name, type, //
					styleAttr.getAttribute(trickleAttr, boolean.class)));
			}
		}
		attributes.putAll(declaredAttributes);

		for (QuickStyleType parent : parents)
			attributes.putAll(parent.getAttributes());
		ELEMENT_STYLE_TYPES.put(element, styled);
		return styled;
	}

	/**
	 * @param style The toolkit inheriting Quick-Style
	 * @return The style-model-value.priority attribute from the Quick-Style toolkit, defining the priority of a model value in a style
	 *         condition
	 */
	public static QonfigAttributeDef.Declared getPriorityAttr(QonfigToolkit style) {
		return style.getAttribute("style-model-value", "priority").getDeclared();
	}

	private final QonfigElementOrAddOn theElement;
	private final List<QuickStyleType> theSuperElements;
	private final Map<String, QuickStyleAttribute<?>> theDeclaredAttributes;
	private final BetterMultiMap<String, QuickStyleAttribute<?>> theAttributes;
	private final QonfigAttributeDef.Declared thePriorityAttr;

	QuickStyleType(QonfigElementOrAddOn element, List<QuickStyleType> superElements, QonfigAttributeDef.Declared priorityAttr, //
		Map<String, QuickStyleAttribute<?>> declaredAttributes, BetterMultiMap<String, QuickStyleAttribute<?>> attributes) {
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
	public List<QuickStyleType> getSuperElements() {
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
	public Map<String, QuickStyleAttribute<?>> getDeclaredAttributes() {
		return theDeclaredAttributes;
	}

	/** @return All style attributes declared for this type and all its {@link #getSuperElements() super types} */
	public BetterMultiMap<String, QuickStyleAttribute<?>> getAttributes() {
		return theAttributes;
	}

	/**
	 * @param name The name of the attribute (may be qualified by element type)
	 * @return The attribute referred to by the given name
	 * @throws IllegalArgumentException If no such attribute could be found, or if multiple attributes match the given name
	 */
	public QuickStyleAttribute<?> getAttribute(String name) throws IllegalArgumentException {
		int dot = name.indexOf('.');
		if (dot < 0) {
			QuickStyleAttribute<?> attr = theDeclaredAttributes.get(name);
			if (attr != null)
				return attr;
			BetterCollection<QuickStyleAttribute<?>> attrs = theAttributes.get(name);
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
			QuickStyleType styled;
			try {
				styled = of(el, null, null);
			} catch (QonfigInterpretationException e) {
				throw new IllegalStateException("Shouldn't happen", e);
			}
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
	public Collection<QuickStyleAttribute<?>> getAttributes(String name) throws IllegalArgumentException {
		int dot = name.indexOf('.');
		if (dot < 0) {
			return theAttributes.get(name);
		} else {
			String elName = name.substring(0, dot);
			QonfigElementOrAddOn el = theElement.getDeclarer().getElementOrAddOn(elName);
			if (el == null)
				throw new IllegalArgumentException("No such element or add-on '" + elName + "'");
			QuickStyleType styled;
			try {
				styled = of(el, null, null);
			} catch (QonfigInterpretationException e) {
				throw new IllegalStateException("Shouldn't happen", e);
			}
			if (styled == null)
				throw new IllegalArgumentException(theElement + " is not related to " + elName);
			return styled.getAttributes(name.substring(dot + 1));
		}
	}

	/**
	 * @param name The name of the attribute (may be qualified by element type)
	 * @param type The type of the attribute
	 * @return The attribute referred to by the given name
	 * @throws IllegalArgumentException If no such attribute could be found, if multiple attributes match the given name, or if the
	 *         attribute does not match the given type
	 */
	public <T> QuickStyleAttribute<? extends T> getAttribute(String name, TypeToken<T> type) throws IllegalArgumentException {
		QuickStyleAttribute<?> attr = getAttribute(name);
		if (!TypeTokens.get().isAssignable(type, attr.getType()))
			throw new IllegalArgumentException(
				"Style attribute " + theElement + "." + name + " is of type " + attr.getType() + ", not " + type);
		return (QuickStyleAttribute<? extends T>) attr;
	}

	/**
	 * @param name The name of the attribute (may be qualified by element type)
	 * @param type The type of the attribute
	 * @return The attribute referred to by the given name
	 * @throws IllegalArgumentException If no such attribute could be found, if multiple attributes match the given name, or if the
	 *         attribute does not match the given type
	 */
	public <T> QuickStyleAttribute<? extends T> getAttribute(String name, Class<T> type) {
		return getAttribute(name, TypeTokens.get().of(type));
	}

	@Override
	public String toString() {
		return theElement.toString();
	}
}
