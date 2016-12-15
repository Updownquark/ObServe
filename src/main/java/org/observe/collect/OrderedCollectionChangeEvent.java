package org.observe.collect;

import java.util.List;

import org.qommons.IntList;

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
	 * @param cause The cause of the event
	 */
	public OrderedCollectionChangeEvent(CollectionChangeType aType, List<E> val, List<E> oldVal, IntList idxs, Object cause) {
		super(aType, val, oldVal, cause);
		indexes = idxs.clone();
		indexes.seal();
	}

	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder();
		switch (type) {
		case add:
			ret.append("added ");
			break;
		case remove:
			ret.append("removed ");
			break;
		case set:
			ret.append("set ");
			break;
		}
		ret.append(" (\n");
		for(int i = 0; i < values.size(); i++) {
			ret.append("\t[").append(indexes.get(i)).append("]: ");
			switch (type) {
			case add:
				ret.append(values.get(i));
				break;
			case remove:
				ret.append(values.get(i));
				break;
			case set:
				ret.append(oldValues.get(i)).append("->").append(values.get(i));
				break;
			}
			ret.append('\n');
		}
		ret.append(')');
		return ret.toString();
	}
}
