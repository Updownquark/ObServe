package org.observe;

/** A subscription to an observable */
@FunctionalInterface
public interface Subscription {
	/** Unsubscribes the observer for this subscription from the observable */
	void unsubscribe();
}
