package org.observe.quick.swing;

import org.qommons.Nameable;

public class SwingTestEntity implements Nameable {
	private String theName;
	private boolean theBoolean;
	private double theDouble = Double.NaN;

	public SwingTestEntity() {}

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

	public boolean getBoolean() {
		return theBoolean;
	}

	public SwingTestEntity setBoolean(boolean b) {
		theBoolean = b;
		return this;
	}

	public double getDouble() {
		return theDouble;
	}

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
