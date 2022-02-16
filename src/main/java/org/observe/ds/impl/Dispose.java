package org.observe.ds.impl;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Tags a method that will be called when the component will no longer be used by the dependency service */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Dispose {
}
