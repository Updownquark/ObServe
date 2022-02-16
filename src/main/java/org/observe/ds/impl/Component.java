package org.observe.ds.impl;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Allows a component class to customize how the dependency service handles it */
@Retention(RUNTIME)
@Target(TYPE)
public @interface Component {
	/** @return Whether the component should provide its services as soon as possible. Default is true. */
	boolean loadImmediately() default true;

	/**
	 * @return The service interfaces this component provides. Default is an empty array, which signifies that the interfaces that the class
	 *         implements should be used.
	 */
	Class<?>[] provides() default {};
}
