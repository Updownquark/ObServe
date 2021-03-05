package org.observe.test;

import java.beans.Transient;
import java.time.Instant;

import org.qommons.collect.BetterList;

/** A result of a test */
public interface TestResult {
	/** @return The suite path of the test that this result is for */
	@Transient
	BetterList<InteractiveTestSuite> getSuitePath();
	/**
	 * @param suitePath The suite path of the test that this result is for
	 * @return This result
	 */
	TestResult setSuitePath(BetterList<InteractiveTestSuite> suitePath);

	/** @return The test that this result is for */
	@Transient
	InteractiveTest getTest();
	/**
	 * @param test The test that this result is for
	 * @return This result
	 */
	TestResult setTest(InteractiveTest test);

	/** @return The time at which execution was initiated on the set of tests that this was a result of */
	Instant getTestTime();

	/** @return The {@link TestingState#getCurrentStage() stage} at which the test failed (if any) */
	TestStage getStage();

	/** @return The {@link InteractiveTest#getStatusMessage() status} message of the test at failure (if any) */
	String getStatus();

	/** @return The {@link InteractiveTest#getEstimatedLength() estimated length} of the test (if failed) */
	double getLength();

	/** @return The {@link InteractiveTest#getEstimatedProgress() estimated progress} of the test at failure (if any) */
	double getProgress();

	/** @return The message passed to {@link InteractiveTesting#fail(String, boolean)}, or null if this is a success record */
	String getFailMessage();
}
