package org.observe.expresso.qonfig;

import java.awt.Image;

import org.observe.expresso.ModelInstantiationException;

/**
 * <p>
 * An application environment that can be used to create a dialog if initialization of the application fails.
 * </p>
 * <p>
 * This is particularly to support the &lt;config> model. Upon initalization, the &lt;config> model will attempt to initialize itself using
 * a configuration file written by a previous instance of the application. In the event that the configuration file is corrupt or cannot be
 * read or parsed for any reason, a dialog will display informing the user of the situation, and allowing them to select a backup
 * configuration to load instead.
 * </p>
 */
public interface AppEnvironment {
	/**
	 * Initializes model values in this environment
	 *
	 * @throws ModelInstantiationException If any model values could not be instantiated
	 */
	void instantiated() throws ModelInstantiationException;

	/**
	 * @return The title of the application
	 * @throws ModelInstantiationException If the title could not be evaluated
	 */
	String getApplicationTitle() throws ModelInstantiationException;

	/**
	 * @return The icon for the application's window
	 * @throws ModelInstantiationException If the icon could not be evaluated
	 */
	Image getApplicationIcon() throws ModelInstantiationException;

	/** Something in which an {@link AppEnvironment} can be configured */
	public interface EnvironmentConfigurable {
		/** @param env The application environment to configure this thing with */
		void setAppEnvironment(AppEnvironment env);
	}
}
