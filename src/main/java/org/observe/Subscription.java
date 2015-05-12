package org.observe;

/** A subscription to an observable */
public interface Subscription {
	/** Unsubscribes this subscription from the observable */
	void unsubscribe();
}
