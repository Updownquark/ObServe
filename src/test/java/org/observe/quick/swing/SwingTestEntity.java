package org.observe.quick.swing;

import org.qommons.Nameable;

public class SwingTestEntity implements Nameable {
	private String theName;
	private boolean theBoolean;

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

	@Override
	public String toString() {
		if (theName == null)
			return String.valueOf(theBoolean);
		else
			return theName + "=" + theBoolean;
	}
}
