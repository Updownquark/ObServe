package org.observe.collect;

import java.util.Collections;
import java.util.List;

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
	public final List<E> values;

	/** The old values from the {@link CollectionChangeType#set} events, or null if this is not a set event */
	public final List<E> oldValues;

	/**
	 * @param aType The common type of the changes
	 * @param val The values that were added, removed, or changed in the collection
	 * @param oldVal The old values from the set events
	 */
	public CollectionChangeEvent(CollectionChangeType aType, List<E> val, List<E> oldVal) {
		type = aType;
		values = Collections.unmodifiableList(val);
		oldValues = oldVal == null ? null : Collections.unmodifiableList(oldVal);
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder();
		switch (type) {
		case add:
			ret.append("added ").append(values);
			break;
		case remove:
			ret.append("removed ").append(values);
			break;
		case set:
			ret.append("set (\n");
			for(int i = 0; i < values.size(); i++) {
				ret.append("\t").append(oldValues.get(i)).append("->").append(values.get(i)).append('\n');
			}
			ret.append(')');
			break;
		}
		return ret.toString();
	}
}
