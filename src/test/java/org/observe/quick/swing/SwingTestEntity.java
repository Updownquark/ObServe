package org.observe.quick.swing;

import org.qommons.Nameable;

/** Simple java bean used by several Quick demo tests */
public class SwingTestEntity implements Nameable {
	private String theName;
	private boolean theBoolean;
	private double theDouble = Double.NaN;

	/** Creates an entity */
	public SwingTestEntity() {}

	/** @param name The name for the entity */
	public SwingTestEntity(String name) {
		theName = name;
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public SwingTestEntity setName(String name) {
		theName = name;
		return this;
	}

	/** @return The boolean on this entity */
	public boolean getBoolean() {
		return theBoolean;
	}

	/**
	 * @param b The boolean for this entity
	 * @return This entity
	 */
	public SwingTestEntity setBoolean(boolean b) {
		theBoolean = b;
		return this;
	}

	/** @return The double value on this entity */
	public double getDouble() {
		return theDouble;
	}

	/**
	 * @param d The double value for this entity
	 * @return This entity
	 */
	public SwingTestEntity setDouble(double d) {
		theDouble = d;
		return this;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		if (theName != null)
			str.append(theName).append('=');
		str.append(theBoolean);
		if (!Double.isNaN(theDouble))
			str.append(" (").append(theDouble).append(')');
		return str.toString();
	}
}
