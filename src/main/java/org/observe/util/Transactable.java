package org.observe.util;

/** Represents a mutable object whose modifications can be batched for increased efficiency */
public interface Transactable {
	/**
	 * Begins a transaction in which modifications to this object may be batched and combined for increased efficiency.
	 *
	 * @param cause An object that may have caused the set of modifications to come. May be null.
	 * @return The transaction to close when calling code is finished modifying this object
	 */
	Transaction startTransaction(Object cause);
}
