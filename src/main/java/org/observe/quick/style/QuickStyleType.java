package org.observe.quick.style;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.observe.util.TypeTokens;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterMultiMap;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

/** Represents style information for a {@link QonfigElementOrAddOn} */
public class QuickStyleType {
	private final QuickStyleSet theStyleSet;
	private final QonfigElementOrAddOn theElement;
	private final List<QuickStyleType> theSuperElements;
	private final Map<String, QuickModelValue<?>> theDeclaredModelValues;
	private final Map<String, QuickModelValue<?>> theModelValues;
	private final Map<String, QuickStyleAttribute<?>> theDeclaredAttributes;
	private final BetterMultiMap<String, QuickStyleAttribute<?>> theAttributes;

	QuickStyleType(QuickStyleSet styleSet, QonfigElementOrAddOn element, List<QuickStyleType> superElements, //
		Map<String, QuickModelValue<?>> declaredModelValues, Map<String, QuickModelValue<?>> modelValues, //
		Map<String, QuickStyleAttribute<?>> declaredAttributes, BetterMultiMap<String, QuickStyleAttribute<?>> attributes) {
		theStyleSet = styleSet;
		theElement = element;
		theSuperElements = superElements;
		theDeclaredModelValues = declaredModelValues;
		theModelValues = modelValues;
		theDeclaredAttributes = declaredAttributes;
		theAttributes = attributes;
	}

	public QuickStyleSet getStyleSet() {
		return theStyleSet;
	}

	public QonfigElementOrAddOn getElement() {
		return theElement;
	}

	public List<QuickStyleType> getSuperElements() {
		return theSuperElements;
	}

	public Map<String, QuickModelValue<?>> getDeclaredModelValues() {
		return theDeclaredModelValues;
	}

	public Map<String, QuickModelValue<?>> getModelValues() {
		return theModelValues;
	}

	public Map<String, QuickStyleAttribute<?>> getDeclaredAttributes() {
		return theDeclaredAttributes;
	}

	public BetterMultiMap<String, QuickStyleAttribute<?>> getAttributes() {
		return theAttributes;
	}

	public QuickStyleAttribute<?> getAttribute(String name) {
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
				styled = theStyleSet.styled(el, null);
			} catch (QonfigInterpretationException e) {
				throw new IllegalStateException("Shouldn't happen", e);
			}
			if (styled == null)
				throw new IllegalArgumentException(theElement + " is not related to " + elName);
			return styled.getAttribute(name.substring(dot + 1));
		}
	}

	public Collection<QuickStyleAttribute<?>> getAttributes(String name) {
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
				styled = theStyleSet.styled(el, null);
			} catch (QonfigInterpretationException e) {
				throw new IllegalStateException("Shouldn't happen", e);
			}
			if (styled == null)
				throw new IllegalArgumentException(theElement + " is not related to " + elName);
			return styled.getAttributes(name.substring(dot + 1));
		}
	}

	public <T> QuickStyleAttribute<? extends T> getAttribute(String name, TypeToken<T> type) {
		QuickStyleAttribute<?> attr = getAttribute(name);
		if (!TypeTokens.get().isAssignable(type, attr.getType()))
			throw new IllegalArgumentException(
				"Style attribute " + theElement + "." + name + " is of type " + attr.getType() + ", not " + type);
		return (QuickStyleAttribute<? extends T>) attr;
	}

	public <T> QuickStyleAttribute<? extends T> getAttribute(String name, Class<T> type) {
		return getAttribute(name, TypeTokens.get().of(type));
	}

	public QuickModelValue<?> getModelValue(String name) {
		int dot = name.indexOf('.');
		if (dot < 0) {
			QuickModelValue<?> mv = theDeclaredModelValues.get(name);
			if (mv != null)
				return mv;
			mv = theModelValues.get(name);
			if (mv == null)
				throw new IllegalArgumentException("No such style model value: " + theElement + "." + name);
			return mv;
		} else {
			String elName = name.substring(0, dot);
			QonfigElementOrAddOn el = theElement.getDeclarer().getElementOrAddOn(elName);
			if (el == null)
				throw new IllegalArgumentException("No such element or add-on '" + elName + "'");
			QuickStyleType styled;
			try {
				styled = theStyleSet.styled(el, null);
			} catch (QonfigInterpretationException e) {
				throw new IllegalStateException("Shouldn't happen", e);
			}
			if (styled == null)
				throw new IllegalArgumentException(theElement + " is not related to " + elName);
			return styled.getModelValue(name.substring(dot + 1));
		}
	}

	public <T> QuickModelValue<T> getModelValue(String name, TypeToken<T> type) {
		QuickModelValue<?> mv = getModelValue(name);
		if (!type.equals(mv.getValueType()))
			throw new IllegalArgumentException("Model value" + theElement + "." + name + " is of type " + mv.getType() + ", not " + type);
		return (QuickModelValue<T>) mv;
	}

	public <T> QuickModelValue<T> getModelValue(String name, Class<T> type) {
		return getModelValue(name, TypeTokens.get().of(type));
	}

	@Override
	public String toString() {
		return theElement.toString();
	}
}
