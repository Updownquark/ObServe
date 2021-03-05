package org.observe.test;

import java.time.Instant;
import java.util.Objects;

import org.qommons.collect.BetterList;

/** Simple concrete implementation of TestResult */
public class DefaultTestResult implements TestResult {
	private BetterList<InteractiveTestSuite> theSuitePath;
	private InteractiveTest theTest;
	private final Instant theTestTime;
	private final TestStage theStage;
	private final String theStatus;
	private final double theLength;
	private final double theProgress;
	private final String theMessage;

	/**
	 * @param testTime The time the test was executed
	 * @param stage The {@link TestingState#getCurrentStage() stage} at which the test failed (if any)
	 * @param status The {@link InteractiveTest#getStatusMessage() status} message of the test at failure (if any)
	 * @param length The {@link InteractiveTest#getEstimatedLength() estimated length} of the test (if failed)
	 * @param progress The {@link InteractiveTest#getEstimatedProgress() estimated progress} of the test at failure (if any)
	 * @param message The failure message
	 */
	public DefaultTestResult(Instant testTime, TestStage stage, String status, double length, double progress, String message) {
		theTestTime = testTime;
		theStage = stage;
		theStatus = status;
		theLength = length;
		theProgress = progress;
		theMessage = message;
	}

	/**
	 * Copy constructor
	 * 
	 * @param testResult The test result whose values to copy
	 */
	public DefaultTestResult(TestResult testResult) {
		theSuitePath = testResult.getSuitePath();
		theTest = testResult.getTest();
		theTestTime = testResult.getTestTime();
		theStage = testResult.getStage();
		theStatus = testResult.getStatus();
		theLength = testResult.getLength();
		theProgress = testResult.getProgress();
		theMessage = testResult.getFailMessage();
	}

	@Override
	public BetterList<InteractiveTestSuite> getSuitePath() {
		return theSuitePath;
	}

	@Override
	public DefaultTestResult setSuitePath(BetterList<InteractiveTestSuite> suitePath) {
		theSuitePath = suitePath;
		return this;
	}

	@Override
	public InteractiveTest getTest() {
		return theTest;
	}

	@Override
	public DefaultTestResult setTest(InteractiveTest test) {
		theTest = test;
		return this;
	}

	@Override
	public Instant getTestTime() {
		return theTestTime;
	}

	@Override
	public TestStage getStage() {
		return theStage;
	}

	@Override
	public String getStatus() {
		return theStatus;
	}

	@Override
	public double getLength() {
		return theLength;
	}

	@Override
	public double getProgress() {
		return theProgress;
	}

	@Override
	public String getFailMessage() {
		return theMessage;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		else if (!(obj instanceof TestResult))
			return false;
		TestResult other = (TestResult) obj;
		return theStage == other.getStage()//
			&& Objects.equals(theStatus, other.getStatus())//
			&& theLength == other.getLength()//
			&& theProgress == other.getProgress()//
			&& Objects.equals(theMessage, other.getFailMessage());
	}
}
