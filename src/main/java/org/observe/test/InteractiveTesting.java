package org.observe.test;

import org.observe.config.ObservableConfig;
import org.qommons.Causable;
import org.qommons.ex.ExConsumer;

/** The interface for a {@link InteractiveTest test} to interact with the framework and the user */
public interface InteractiveTesting extends TestingState {
	/** @return The global testing environment */
	InteractiveTestEnvironment getEnv();

	/** @return The user interface */
	UserInteraction getUI();

	/** @return The test's static configuration, if set */
	ObservableConfig getConfig();

	/** Alerts the testing architecture that the test has made some progress, allowing the tester to update UI status */
	void testProgress();

	/**
	 * Marks the test as failed
	 *
	 * @param message The message describing what failed in the test
	 * @param exitTest Whether the test must exit. A test on multiple criteria may be able to progress even if it fails in one place.
	 */
	void fail(String message, boolean exitTest);

	/** @param action An action to invoke after the test has completed, failed, or been canceled */
	void postTest(ExConsumer<Causable, ?> action);
}
