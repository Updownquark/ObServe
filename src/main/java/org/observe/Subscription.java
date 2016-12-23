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
				if (unsubscribe(subs[s]))
					subs[s] = null;
			}
		};
	}

	/**
	 * @param sub The subscription to unsubscribe
	 * @return If the subscription was non-null
	 */
	static boolean unsubscribe(Subscription sub) {
		if (sub != null) {
			sub.unsubscribe();
			return true;
		} else
			return false;
	}
}
