package org.observe.collect;

import java.util.Collection;

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
	 * @param idxs The indexes of the elements added, removed, or changed, in the same order as <code>val</code>
	 */
	public OrderedCollectionChangeEvent(CollectionChangeType aType, Collection<E> val, IntList idxs) {
		super(aType, val);
		indexes = idxs;
		indexes.seal();
	}

	/**
	 * @param aType The common type of the changes
	 * @param val The values that were added, removed, or changed in the collection
	 * @param idx The index of the element added, removed, or changed
	 */
	public OrderedCollectionChangeEvent(CollectionChangeType aType, E val, int idx) {
		super(aType, val);
		indexes = new IntList(new int[] {idx});
		indexes.seal();
	}
}
