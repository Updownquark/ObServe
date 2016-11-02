package org.observe;

/** A subscription to an observable */
@FunctionalInterface
public interface Subscription {
	/** Unsubscribes the observer for this subscription from the observable */
	void unsubscribe();

	/**
	 * @param subs The subscriptions to bundle
	 * @return A single subscription whose {@link #unsubscribe()} method unsubscribes all of the given subscriptions
	 */
	static Subscription forAll(Subscription... subs) {
		return () -> {
			for (int s = 0; s < subs.length; s++) {
				if (subs[s] != null) {
					subs[s].unsubscribe();
					subs[s] = null;
				}
			}
		};
	}
}
