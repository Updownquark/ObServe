package org.observe.expresso.qonfig;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * <p>
 * This annotation is to be placed on {@link ExElement.Def ExElement.Def} extensions to facilitate {@link ElementTypeTraceability}
 * traceability.
 * </p>
 * <p>
 * This annotation is specifically for types whose {@link QonfigAttributeGetter}- and {@link QonfigChildGetter}- tagged getter methods are
 * for a multiple Qonfig types.
 * </p>
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface ExMultiElementTraceable {
	/**
	 * @return The traceability for each Qonfig type that the {@link ExElement.Def ExElement.Def} implementation tagged by this annotation
	 *         has {@link QonfigAttributeGetter}- and {@link QonfigChildGetter}- tagged getter methods for
	 */
	ExElementTraceable[] value();
}
