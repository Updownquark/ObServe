package org.observe.quick.style;

import com.google.common.reflect.TypeToken;

/**
 * An attribute whose value for a given element will be determined by the
 * highest-{@link StyleApplicationDef#compareTo(StyleApplicationDef) priority} {@link QuickStyleValue} that
 * {@link StyleApplicationDef#applies(org.qommons.config.QonfigElement) applies} to it.
 *
 * @param <T> The type of the attribute's values
 */
public class QuickStyleAttribute<T> {
	private final QuickTypeStyle theDeclarer;
	private final String theName;
	private final TypeToken<T> theType;
	private final boolean isTrickleDown;

	/**
	 * @param declarer The type that declared this style attribute
	 * @param name The name for the attribute
	 * @param type The type of the attribute
	 * @param trickleDown Whether, if not {@link QuickStyleValue} {@link StyleApplicationDef#applies(org.qommons.config.QonfigElement)
	 *        applies} to an element, its value will be that of its most recent ancestor element that for which this attribute also applies
	 */
	public QuickStyleAttribute(QuickTypeStyle declarer, String name, TypeToken<T> type, boolean trickleDown) {
		theDeclarer=declarer;
		theName=name;
		theType=type;
		isTrickleDown = trickleDown;
	}

	/** @return The type that declared this style attribute */
	public QuickTypeStyle getDeclarer() {
		return theDeclarer;
	}

	/** @return The name of this attribute */
	public String getName() {
		return theName;
	}

	/** @return The type of this attribute's values */
	public TypeToken<T> getType() {
		return theType;
	}

	/**
	 * @return Whether, if not {@link QuickStyleValue} {@link StyleApplicationDef#applies(org.qommons.config.QonfigElement) applies} to an
	 *         element, its value will be that of its most recent ancestor element that for which this attribute also applies
	 */
	public boolean isTrickleDown() {
		return isTrickleDown;
	}

	@Override
	public String toString() {
		return theDeclarer + "." + theName + "(" + theType + ")";
	}
}
