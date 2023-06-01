package org.observe.supertest;

import java.io.File;
import java.time.Duration;

import org.junit.Test;
import org.qommons.QommonsUtils;
import org.qommons.testing.TestHelper;

/** A long chain tester, for standalone testing */
public class LongChainTester extends ObservableChainTester {
	/**
	 * Generates a set of random test cases to test ObServe functionality. If there are previous known failures, this method will execute
	 * them first. The first failure will be persisted and the test will end.
	 */
	@Test
	public void superTest() {
		Duration testDuration = Duration.ofMinutes(10);
		int maxFailures = 1;
		System.out.println(
			"Executing up to " + QommonsUtils.printTimeLength(testDuration.toMillis()) + " of tests with max " + maxFailures + " failures");
		TestHelper.createTester(getClass())//
		.withRandomCases(-1)// No case number limit
		.withMaxRememberedFixes(100)// Help prevent regression on recent fixes
		.withMaxCaseDuration(Duration.ofMinutes(3)) // Since we're using progress interval checking, this can be pretty long
		.withMaxTotalDuration(testDuration)//
		.withMaxProgressInterval(Duration.ofSeconds(10))// If a process doesn't make any progress in 10s, something's wrong
		.withMaxFailures(maxFailures)//
		.withConcurrency(max -> max - 1)// Use all but 1 of the system's CPUs
		.withPersistenceDir(new File("src/test/java/org/observe/supertest"), false)// Where to write the failure file
		.withPlacemarks("Transaction", "Modification").withDebug(true)//
		.execute()//
		.printResults().throwErrorIfFailed();
	}
}
