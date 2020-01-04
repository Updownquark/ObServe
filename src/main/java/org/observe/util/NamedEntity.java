package org.observe.util;

import org.observe.util.ObjectMethodOverride.ObjectMethod;
import org.qommons.Nameable;

public interface NamedEntity extends Nameable {
	@Override
	@ObjectMethodOverride(ObjectMethod.toString)
	String getName();
}
