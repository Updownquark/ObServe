package org.observe;

import java.util.Collection;

/** A subscription to an observable */
@FunctionalInterface
public interface Subscription extends AutoCloseable {
	/** Unsubscribes the observer for this subscription from the observable */
	void unsubscribe();

	@Override
	default void close() {
		unsubscribe();
	}

	/** @return Generally false, but true if this subscription is just a placeholder that does nothing */
	default boolean isTrivial() {
		return false;
	}

	/**
	 * @param subs The subscriptions to bundle
	 * @return A single subscription whose {@link #unsubscribe()} method unsubscribes all of the given subscriptions
	 */
	static Subscription forAll(Subscription... subs) {
		return new MultiSubscription(subs);
	}

	/**
	 * @param subs The subscriptions to bundle
	 * @return A single subscription whose {@link #unsubscribe()} method unsubscribes all of the given subscriptions
	 */
	static Subscription forAll(Collection<? extends Subscription> subs) {
		return forAll(subs.toArray(new Subscription[subs.size()]));
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

	/** A subscription that does nothing on {@link #unsubscribe()} */
	static Subscription NONE = new Subscription() {
		@Override
		public void unsubscribe() {}

		@Override
		public boolean isTrivial() {
			return true;
		}

		@Override
		public String toString() {
			return "NONE";
		}
	};

	/** A subscription composed of any number of others */
	static class MultiSubscription implements Subscription {
		private final Subscription[] subs;

		MultiSubscription(Subscription[] subs) {
			this.subs = subs;
		}

		@Override
		public void unsubscribe() {
			for (int s = 0; s < subs.length; s++) {
				if (Subscription.unsubscribe(subs[s]))
					subs[s] = null;
			}
		}

		@Override
		public boolean isTrivial() {
			for (Subscription sub : subs) {
				if (sub != null && !sub.isTrivial())
					return false;
			}
			return true;
		}
	}
}
