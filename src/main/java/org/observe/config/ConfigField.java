package org.observe.config;

import static java.lang.annotation.ElementType.METHOD;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Configuration for a {@link ObservableConfig}-persisted field */
@Retention(RetentionPolicy.RUNTIME)
@Target(METHOD)
public @interface ConfigField {
	/** @return Whether this field, when it is set to its same value, will still fire events up the config hierarchy */
	boolean refreshing() default false;
}
