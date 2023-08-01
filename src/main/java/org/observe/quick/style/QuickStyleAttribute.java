package org.observe.quick.style;

import org.qommons.Named;
import org.qommons.SelfDescribed;

import com.google.common.reflect.TypeToken;

public class QuickStyleAttribute<T> implements Named, SelfDescribed {
	private final QuickStyleAttributeDef theDefinition;
	private final TypeToken<T> theType;

	public QuickStyleAttribute(QuickStyleAttributeDef definition, TypeToken<T> type) {
		theDefinition = definition;
		theType = type;
	}

	public QuickStyleAttributeDef getDefinition() {
		return theDefinition;
	}

	public TypeToken<T> getType() {
		return theType;
	}

	@Override
	public String getName() {
		return theDefinition.getName();
	}

	@Override
	public String getDescription() {
		return theDefinition.getDescription();
	}

	@Override
	public int hashCode() {
		return theDefinition.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof QuickStyleAttribute))
			return false;
		QuickStyleAttribute<T> other = (QuickStyleAttribute<T>) obj;
		return theDefinition.equals(other.theDefinition) && theType.equals(other.theType);
	}

	@Override
	public String toString() {
		return theDefinition.toString();
	}
}
