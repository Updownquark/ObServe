package org.observe.expresso.qonfig;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation to alert {@link ElementTypeTraceability traceability} that a getter on an {@link ExElement.Def} extension tagged with
 * {@link ExElementTraceable} or {@link ExMultiElementTraceable} provides the {@link ExElement} definition for a Qonfig child
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface QonfigChildGetter {
	/**
	 * For {@link ExElement.Def}s tagged with {@link ExMultiElementTraceable}, this is the (potentially qualified) type of the Qonfig
	 * element or add-on owning the child this getter returns the representation of
	 */
	String asType() default "";

	/** The name of the child role this getter returns the {@link ExElement} definition for */
	String value();
}
