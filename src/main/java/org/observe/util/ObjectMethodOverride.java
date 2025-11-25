package org.observe.util;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.observe.util.EntityReflector.MethodSignature;

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
		hashCode(new MethodSignature("hashCode", new Class[0])),
		/** {@link Object#equals(Object)} */
		equals(new MethodSignature("equals", new Class[] { Object.class })),
		/** {@link Object#toString()} */
		toString(new MethodSignature("toString", new Class[0]));

		/** The method signature of the {@link Object} method */
		public final MethodSignature signature;

		private ObjectMethod(MethodSignature signature) {
			this.signature = signature;
		}
	}

	/** @return The object method to override */
	ObjectMethod value();
}
