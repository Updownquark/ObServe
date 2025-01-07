package org.observe.util;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** An annotation to add to getters in entities reflected using {@link EntityReflector} to configure certain properties about it */
@Retention(RUNTIME)
@Target(METHOD)
public @interface EntityField {
	/** @return The name for the field. By default, this is determined by the name of the getter. */
	String name() default "";
}
