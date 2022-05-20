package org.observe;

/** A type of value holder that may fire events */
public interface Eventable {
	/**
	 * @return Whether this eventable is currently firing an event. This method is required to return true if an event is currently being
	 *         fired on the thread this method is called from. Thread info is not required to be kept by this eventable, in which case true
	 *         may be returned during event firing even when this is called from a different thread.
	 */
	boolean isEventing();
}
