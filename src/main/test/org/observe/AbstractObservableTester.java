package org.observe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A utility class for testing observable types
 *
 * @param <T> The type of the value of the observable (if it is a valued observable)
 */
public abstract class AbstractObservableTester<T> {
	private Subscription theSyncSubscription;
	private int theOldOpCount;
	private int theOpCount;

	/**
	 * Checks this tester's internal state and checks it against the given value
	 *
	 * @param expected The value to check against this tester's observable's value
	 */
	public void check(T expected) {
		check(expected, 0, 0);
	}

	/**
	 * Checks this tester's internal state and checks it against the given value and expected number of operations
	 *
	 * @param expected The value to check against this tester's observable's value
	 * @param ops The number of events expected to have occurred since the last check against this tester
	 */
	public void check(T expected, int ops) {
		check(expected, ops, ops);
	}

	/**
	 * Checks this tester's internal state and checks it against the given value and expected number of operations
	 *
	 * @param expected The value to check against this tester's observable's value
	 * @param minOps The minimum number of events expected to have occurred since the last check against this tester
	 * @param maxOps The maximum number of events expected to have occurred since the last check against this tester
	 */
	public void check(T expected, int minOps, int maxOps) {
		checkOps(minOps, maxOps);
		checkSynced();
		checkValue(expected);
	}

	/**
	 * Checks the actual number of operations that have occurred since the last check against the given value
	 *
	 * @param ops The number of events expected to have occurred since the last check against this tester
	 */
	public void checkOps(int ops) {
		checkOps(ops, ops);
	}

	/**
	 * Checks the actual number of operations that have occurred since the last check against the given values
	 *
	 * @param minOps The minimum number of events expected to have occurred since the last check against this tester
	 * @param maxOps The maximum number of events expected to have occurred since the last check against this tester
	 */
	public void checkOps(int minOps, int maxOps) {
		int ops = theOpCount - theOldOpCount;
		if (minOps == maxOps && maxOps > 0)
			assertEquals(minOps, ops);
		else {
			if (minOps > 0)
				assertTrue("Expected at least " + minOps + " operations, got " + ops, ops >= minOps);
			else if (maxOps > 0 && maxOps < Integer.MAX_VALUE)
				assertTrue("Expected at most " + maxOps + " operations, got " + ops, ops <= maxOps);
		}
		theOldOpCount = theOpCount;
	}

	/**
	 * Allows turning on/off of synchronization between this tester's observable and its internal state
	 * 
	 * @param synced Whether this tester should be synchronizing its observable and its internal state
	 */
	public void setSynced(boolean synced) {
		if (synced == (theSyncSubscription != null))
			return;
		if (synced)
			theSyncSubscription = sync();
		else {
			theSyncSubscription.unsubscribe();
			theSyncSubscription = null;
		}
	}

	/** Increments this tester's operation count */
	protected void op() {
		theOpCount++;
	}

	/** @throws AssertionError if this tester's synchronized value is not the same as the dynamic value of the observable */
	public abstract void checkSynced();

	/**
	 * @param expected The expected value
	 * @throws AssertionError if this tester's synchronized value is not the same as the given expected value
	 */
	public abstract void checkValue(T expected);

	/**
	 * Synchronizes this tester's observable value with its internal synchronized value
	 *
	 * @return The subscription that, if unsubscribed, will terminate the synchronization
	 */
	protected abstract Subscription sync();
}
