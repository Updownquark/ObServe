package org.observe.collect;

/** Represents a change to a collection */
public enum CollectionChangeType {
	/** One or more elements were added to the collection */
	add,
	/** One or more elements were removed from the collection */
	remove,
	/** One or more elements had their values replaced or modified */
	set;
}
