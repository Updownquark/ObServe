package org.observe.ds.impl;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Tags a method on a component that will be called after all the components' initial dependencies have been resolved and installed */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Activate {
}
