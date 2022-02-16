package org.observe.ds.impl;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Tags a method that accepts a service that the component depends upon. The release method will be called when the dependency is no longer
 * available.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Release {
}
