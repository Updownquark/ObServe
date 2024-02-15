package org.observe.expresso.qonfig;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation to alert {@link ElementTypeTraceability traceability} that a static method on an {@link ExElement.Def} extension tagged with
 * {@link ExElementTraceable} or {@link ExMultiElementTraceable} accepts a {@link ElementTypeTraceability.SingleTypeTraceabilityBuilder} to
 * configure traceability
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface TraceabilityConfiguration {
}
