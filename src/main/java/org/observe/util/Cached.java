package org.observe.util;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Tag an entity's default getter method with this annotation, and the entity's default method will be lazily called once and the value
 * cached for future calls.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface Cached {
}
