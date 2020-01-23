package org.observe.util;

import org.observe.util.ObjectMethodOverride.ObjectMethod;
import org.qommons.Nameable;

/** A Nameable interface that serves as a nice base class for observable entity interfaces */
public interface NamedEntity extends Nameable {
	@Override
	@ObjectMethodOverride(ObjectMethod.toString)
	String getName();
}
