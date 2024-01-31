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
 * for a single Qonfig type.
 * </p>
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface ExElementTraceable {
	/**
	 * The "Toolkit.Name vM.m" representation of the toolkit whose element is represented/handled by the {@link ExElement.Def}
	 * implementation tagged with this annotation
	 */
	String toolkit();

	/**
	 * The name of the Qonfig element that is represented/handled by the {@link ExElement.Def} implementation tagged with this annotation
	 */
	String qonfigType();

	/**
	 * @return The {@link ExElement.Interpreted ExElement.Interpreted} implementation that the {@link ExElement.Def ExElement.Def}
	 *         implementation tagged by this annotation produces for its interpretation.
	 */
	Class<?> interpretation() default void.class;

	/**
	 * @return The {@link ExElement} implementation that the {@link ExElement.Def ExElement.Def} implementation tagged by this annotation
	 *         produces for its instance element.
	 */
	Class<?> instance() default void.class;
}
