package org.observe.ds.impl;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** A tag for a method or field in a DS component that may be configured in the file the component is loaded from */
@Retention(RUNTIME)
@Target({ METHOD, FIELD })
public @interface Configure {
	/** @return The name of the configuration for the component */
	String value();

	/**
	 * @return Whether configuration for this item is optional. If false, the component will not be loaded without a value for this
	 *         configuration item.
	 */
	boolean optional() default false;
}
