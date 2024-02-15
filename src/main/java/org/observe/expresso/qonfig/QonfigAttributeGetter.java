package org.observe.expresso.qonfig;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation to alert {@link ElementTypeTraceability traceability} that a getter on an {@link ExElement.Def} extension tagged with
 * {@link ExElementTraceable} or {@link ExMultiElementTraceable} provides a representation of the value of a Qonfig attribute or element
 * value
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface QonfigAttributeGetter {
	/**
	 * For {@link ExElement.Def}s tagged with {@link ExMultiElementTraceable}, this is the (potentially qualified) type of the Qonfig
	 * element or add-on owning the attribute or element value this getter returns the representation of
	 */
	String asType() default "";

	/**
	 * The name of the attribute this getter returns the representation of, <code>""</code> if the getter returns a representation of the
	 * element value
	 */
	String value() default "";
}
