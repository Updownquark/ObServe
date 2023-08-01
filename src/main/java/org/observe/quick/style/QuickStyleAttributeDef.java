package org.observe.quick.style;

import org.observe.expresso.VariableType;
import org.qommons.Named;
import org.qommons.SelfDescribed;

/**
 * An attribute whose value for a given element will be determined by the
 * highest-{@link StyleApplicationDef#compareTo(StyleApplicationDef) priority} {@link QuickStyleValue} that
 * {@link StyleApplicationDef#applies(org.qommons.config.QonfigElement) applies} to it.
 */
public class QuickStyleAttributeDef implements Named, SelfDescribed {
	private final QuickTypeStyle theDeclarer;
	private final String theName;
	private final VariableType theType;
	private final boolean isTrickleDown;
	private final String theDescription;

	/**
	 * @param declarer The type that declared this style attribute
	 * @param name The name for the attribute
	 * @param type The type of the attribute
	 * @param trickleDown Whether, if not {@link QuickStyleValue} {@link StyleApplicationDef#applies(org.qommons.config.QonfigElement)
	 *        applies} to an element, its value will be that of its most recent ancestor element that for which this attribute also applies
	 * @param description A description of the attribute
	 */
	public QuickStyleAttributeDef(QuickTypeStyle declarer, String name, VariableType type, boolean trickleDown, String description) {
		theDeclarer=declarer;
		theName=name;
		theType=type;
		isTrickleDown = trickleDown;
		theDescription = description;
	}

	/** @return The type that declared this style attribute */
	public QuickTypeStyle getDeclarer() {
		return theDeclarer;
	}

	/** @return The name of this attribute */
	@Override
	public String getName() {
		return theName;
	}

	/** @return The type of this attribute's values */
	public VariableType getType() {
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
	public String getDescription() {
		return theDescription;
	}

	@Override
	public String toString() {
		return theDeclarer + "." + theName + "(" + theType + ")";
	}
}
