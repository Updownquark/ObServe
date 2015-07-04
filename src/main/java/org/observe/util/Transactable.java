package org.observe.util;

/** Represents a mutable object whose modifications can be batched for increased efficiency */
public interface Transactable {
	/**
	 * Begins a transaction in which inspections and/or modifications to this object may be batched and combined for increased efficiency.
	 *
	 * @param write Whether to lock this object for writing (prevents all access to controlled properties of the object outside of this
	 *            thread) or just for reading (prevents all modification to this object, this thread included).
	 * @param cause An object that may have caused the set of modifications to come. May be null.
	 * @return The transaction to close when calling code is finished accessing or modifying this object
	 */
	Transaction lock(boolean write, Object cause);
}
