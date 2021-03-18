package org.observe.test;

import java.util.List;

/** The state of testing */
public interface TestingState {
	/** @return The suite path of the currently executing test */
	List<InteractiveTestSuite> getSuiteStack();

	/** @return The suite owning the currently executing test */
	default InteractiveTestSuite getTopSuite() {
		return getSuiteStack().get(getSuiteStack().size() - 1);
	}

	/** @return The test that is currently executing */
	InteractiveTest getCurrentTest();

	/** @return The stage of the current test */
	TestStage getCurrentStage();

	/** @return Whether testing is currently occurring */
	boolean isTesting();

	/** @return The list of test failures that have occurred so far in this testing session */
	List<TestResult> getFailuresSoFar();
}
