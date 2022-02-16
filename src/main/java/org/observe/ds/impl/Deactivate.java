package org.observe.ds.impl;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Tags a method that the dependency service will call on a component when any of its required dependencies are no longer fulfilled. The
 * {@link Activate activate} method will be called again if they become fulfilled again.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Deactivate {

}
