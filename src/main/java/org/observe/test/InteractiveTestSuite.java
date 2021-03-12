package org.observe.test;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;

/** A set of tests or sub-suites to execute */
public interface InteractiveTestSuite extends InteractiveTestOrSuite {
	/** @return The parent of this suite. Only null for the {@link InteractiveTestingService}. */
	InteractiveTestSuite getParent();

	/**
	 * @param test The test to add to the suite
	 * @param configLocation The location of the XML configuration for the test--may be null
	 */
	void addTestCase(InteractiveTest test, String configLocation);

	/**
	 * Modifies or adds a sub-suite to this test suite. If a suite with the given name does not already exist, one will be created.
	 *
	 * @param suiteName The name for the test suite
	 * @param configLocation The location of the XML configuration for the suite--may be null
	 * @param sequential Whether the suite is to be {@link InteractiveTestSuite#isSequential() sequential}
	 * @param suite Configures the new test suite
	 */
	void addTestSuite(String suiteName, String configLocation, boolean sequential, Consumer<InteractiveTestSuite> suite);

	/**
	 * A sequential suite is one whose content is dependent on the execution of all the content before it. A test under a sequential suite
	 * cannot execute on its own (unless it is the first in the suite)--the entire suite of tests leading up to that test must be executed
	 * first.
	 *
	 * @return Whether this suite is sequential
	 */
	boolean isSequential();

	/** @return This suite's content of {@link InteractiveTest tests} and {@link InteractiveTestSuite sub-suites} */
	ObservableCollection<InteractiveTestOrSuite> getContent();

	/**
	 * @param testName The name of the test to get configuration for
	 * @return The static configuration for the given test
	 * @throws IOException If the location has been configured, but cannot be accessed or read
	 */
	ObservableConfig getConfig(String testName) throws IOException;

	/**
	 * Executes this suite or one test in it
	 *
	 * @param test The test to execute, or null to execute the entire suite
	 * @param ui The UI to expose to the tests
	 * @param state Accepts test state whenever it changes
	 * @return The results of all the executed tests
	 */
	List<TestResult> execute(InteractiveTest test, UserInteraction ui, Consumer<TestingState> state);

	/**
	 * Executes all tests in this suite
	 *
	 * @param ui The UI to expose to the tests
	 * @param state Accepts test state whenever it changes
	 * @return The results of all the executed tests
	 */
	default List<TestResult> executeAll(UserInteraction ui, Consumer<TestingState> state) {
		return execute(null, ui, state);
	}

	/**
	 * @param testName The name of the test to get results for
	 * @return The results of the given test in this suite
	 */
	ObservableCollection<TestResult> getTestResults(String testName);

	/** @return Test results of all tests in this suite */
	ObservableCollection<TestResult> getAllTestResults();
}
