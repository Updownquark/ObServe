package org.observe.quick.style;

import com.google.common.reflect.TypeToken;

public class QuickStyleAttribute<T> {
	private final QuickStyleType theDeclarer;
	private final String theName;
	private final TypeToken<T> theType;
	private final boolean isTrickleDown;

	public QuickStyleAttribute(QuickStyleType declarer, String name, TypeToken<T> type, boolean trickleDown) {
		theDeclarer=declarer;
		theName=name;
		theType=type;
		isTrickleDown = trickleDown;
	}

	public QuickStyleType getDeclarer() {
		return theDeclarer;
	}

	public String getName() {
		return theName;
	}

	public TypeToken<T> getType() {
		return theType;
	}

	public boolean isTrickleDown() {
		return isTrickleDown;
	}

	@Override
	public String toString() {
		return theDeclarer + "." + theName + "(" + theType + ")";
	}
}
