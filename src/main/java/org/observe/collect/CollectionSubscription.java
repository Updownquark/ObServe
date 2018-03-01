package org.observe.collect;

import org.observe.Subscription;

/** A subscription to a collection that can be canceled */
@FunctionalInterface
public interface CollectionSubscription extends Subscription {
	/**
	 * Terminates the subscription so that the observer will receive no more change events
	 * 
	 * @param removeAll Whether to fire {@link CollectionChangeType#remove remove} events for all the current values in the collection
	 */
	void unsubscribe(boolean removeAll);

	/** Same as {@link #unsubscribe(boolean) unsubscribe(false)} */
	@Override
	default void unsubscribe() {
		unsubscribe(false);
	}

	/** @return A generic Subscription that calls {@link #unsubscribe(boolean) unsubscribe(true)} */
	default Subscription removeAll() {
		return () -> unsubscribe(true);
	}
}
