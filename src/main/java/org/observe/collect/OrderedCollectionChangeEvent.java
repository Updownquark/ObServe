package org.observe.collect;

import java.util.List;

import prisms.util.IntList;

/**
 * A change event to an ordered collection
 *
 * @param <E> The type of elements in the collection
 */
public class OrderedCollectionChangeEvent<E> extends CollectionChangeEvent<E> {
	/** The indexes of elements that were added, removed, or changed in the collection, in the same order as {@link #values} */
	public IntList indexes;

	/**
	 * @param aType The common type of the changes
	 * @param val The values that were added, removed, or changed in the collection
	 * @param oldVal The old values from the set events
	 * @param idxs The indexes of the elements added, removed, or changed, in the same order as <code>val</code>
	 */
	public OrderedCollectionChangeEvent(CollectionChangeType aType, List<E> val, List<E> oldVal, IntList idxs) {
		super(aType, val, oldVal);
		indexes = idxs;
		indexes.seal();
	}
}
