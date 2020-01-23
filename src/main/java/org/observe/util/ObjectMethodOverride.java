package org.observe.util;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Tag an entity's default getter method with this annotation, and the entity's default method will be called once and the value cached for
 * future calls.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface ObjectMethodOverride {
	/** An enumeration of object methods that may be overridden using this tag */
	public static enum ObjectMethod {
		/** {@link Object#hashCode()} */
		hashCode,
		/** {@link Object#equals(Object)} */
		equals,
		/** {@link Object#toString()} */
		toString;
	}

	/** @return The object method to override */
	ObjectMethod value();
}
