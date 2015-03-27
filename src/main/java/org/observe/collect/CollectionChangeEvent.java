package org.observe.collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * Represents a set of changes to a collection with a common {@link CollectionChangeType type}.
 *
 * @param <E> The type of element in the changed collection
 */
public class CollectionChangeEvent<E> {
	/** The type of the changes that this event represents */
	public final CollectionChangeType type;

	/**
	 * The values that were {@link CollectionChangeType#add added}, {@link CollectionChangeType#remove removed}, or
	 * {@link CollectionChangeType#set changed} in the collection
	 */
	public final Collection<E> values;

	/**
	 * @param aType The common type of the changes
	 * @param val The values that were added, removed, or changed in the collection
	 */
	public CollectionChangeEvent(CollectionChangeType aType, Collection<E> val) {
		type = aType;
		values = Collections.unmodifiableCollection(val);
	}

	/**
	 * @param aType The common type of the changes
	 * @param val The values that were added, removed, or changed in the collection
	 */
	public CollectionChangeEvent(CollectionChangeType aType, E... val) {
		type = aType;
		values = Collections.unmodifiableCollection(Arrays.asList(val));
	}
}
