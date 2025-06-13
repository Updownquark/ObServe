package org.observe.collect;

/** Represents a change to a collection */
public enum CollectionChangeType {
	/** One or more elements were added to the collection */
	add,
	/** One or more elements were removed from the collection */
	remove,
	/** One or more elements had their values replaced or modified */
	set;

	/* These methods are here because the syntax
	 *
	 * if(event.type.isRemove())
	 *
	 * is so much neater than
	 *
	 * if(event.type==CollectionChangeType.remove)
	 *
	 * If java would let me do something like
	 *
	 * if(event.type==remove)
	 *
	 * this wouldn't be needed.
	 */

	/** @return Whether <code>this == {@link CollectionChangeType#add}</code> */
	public boolean isAdd() {
		return this == CollectionChangeType.add;
	}

	/** @return Whether <code>this == {@link CollectionChangeType#remove}</code> */
	public boolean isRemove() {
		return this == CollectionChangeType.remove;
	}

	/** @return Whether <code>this == {@link CollectionChangeType#set}</code> */
	public boolean isSet() {
		return this == CollectionChangeType.set;
	}
}
