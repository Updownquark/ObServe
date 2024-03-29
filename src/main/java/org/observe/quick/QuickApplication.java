package org.observe.quick;

import org.observe.Observable;
import org.observe.expresso.ModelInstantiationException;

/** Runs a Quick application from an application setup file configured as quick-app.qtd */
public interface QuickApplication {
	/**
	 * Runs the application
	 *
	 * @param doc The document containing the Quick configuration for the application
	 * @throws ModelInstantiationException If an error occurs initializing the application
	 */
	void runApplication(QuickDocument doc, Observable<?> until) throws ModelInstantiationException;

	void update(QuickDocument doc) throws ModelInstantiationException;
}
