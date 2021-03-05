package org.observe.test;

import org.observe.ObservableValue;

/**
 * The root interface of the interactive testing API. Allows the installation of tests, suites, and the configuration of the test
 * environment
 */
public interface InteractiveTestingService extends InteractiveTestSuite {
	/** @return The state of any testing that is currently executing */
	ObservableValue<TestingState> getCurrentTest();

	/** Cancels the current test, if any */
	void cancelTest();

	/** @return The global test environment */
	InteractiveTestEnvironment getEnv();

	/**
	 * Adds a value to the global test {@link #getEnv() environment}
	 *
	 * @param <T> The type of the value to add
	 * @param name The name to add the value under
	 * @param value The value to add
	 */
	<T> void addValue(String name, ObservableValue<T> value);
}
