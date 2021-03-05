package org.observe.config;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** A tag for an method in an entity that is owned by another entity to reference the owner */
@Retention(RUNTIME)
@Target(METHOD)
public @interface ParentReference {
}
