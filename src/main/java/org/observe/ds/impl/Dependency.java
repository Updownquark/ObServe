package org.observe.ds.impl;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** A tag for a method with a parameter that is a dependency for the dependency service */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Dependency {
	/** @return The minimum number of instances of the dependency that must be available to load the component. Default is 1. */
	int min() default 1;

	/** @return The maximum number of instances of the dependency to install in the component. Default is 1. */
	int max() default 1;

	/**
	 * @return Whether the dependency is dynamic
	 * @see org.observe.ds.Dependency#isDynamic()
	 */
	boolean dynamic() default false;
}
