package org.observe;

import org.qommons.Causable;
import org.qommons.Transactable;

/** An object that publishes its changes */
public interface CausableChanging {
	/**
	 * <p>
	 * This method returns an observable that fires whenever this object changes.
	 * </p>
	 * <p>
	 * Unlike any events that this object may fire in response to individual changes, this event represents an entire
	 * {@link Transactable#lock(boolean, Object) transaction} of changes. An implementation should attempt to only fire this observable when
	 * all changes in the transaction are complete.
	 * </p>
	 *
	 * @return An observable that fires when this object changes
	 */
	Observable<? extends Causable> simpleChanges();
}
