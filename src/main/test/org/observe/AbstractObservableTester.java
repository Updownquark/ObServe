package org.observe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class AbstractObservableTester<T> {
	private Subscription theSyncSubscription;
	private int theOldOpCount;
	private int theOpCount;

	public void check(T expected) {
		check(expected, 0, 0);
	}

	public void check(T expected, int ops) {
		check(expected, ops, ops);
	}

	public void check(T expected, int minOps, int maxOps) {
		checkOps(minOps, maxOps);
		checkSynced();
		checkValue(expected);
	}

	public void checkOps(int ops) {
		checkOps(ops, ops);
	}

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
