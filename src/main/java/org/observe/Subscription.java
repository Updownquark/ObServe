package org.observe;

/** A subscription to an observable */
public interface Subscription {
	/** Unsubscribes the observer for this subscription from the observable */
	void unsubscribe();
}
