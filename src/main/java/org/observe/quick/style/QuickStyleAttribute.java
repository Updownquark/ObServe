package org.observe.quick.style;

import org.qommons.Named;
import org.qommons.SelfDescribed;

import com.google.common.reflect.TypeToken;

/**
 * An attribute that can be affected by Quick styles
 *
 * @param <T> The type of the attribute's value
 */
public class QuickStyleAttribute<T> implements Named, SelfDescribed {
	private final QuickStyleAttributeDef theDefinition;
	private final TypeToken<T> theType;

	/**
	 * @param definition The definition of the attribute
	 * @param type The type of the attribute
	 */
	public QuickStyleAttribute(QuickStyleAttributeDef definition, TypeToken<T> type) {
		theDefinition = definition;
		theType = type;
	}

	/** @return The definition of the attribute */
	public QuickStyleAttributeDef getDefinition() {
		return theDefinition;
	}

	/** @return The type of the attribute */
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
		return theDefinition.equals(other.theDefinition);
	}

	@Override
	public String toString() {
		return theDefinition.toString();
	}
}
