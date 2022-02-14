package org.observe.assoc;

import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollectionEvent;
import org.qommons.collect.ElementId;

/**
 * An event representing a change to a {@link ObservableMap}
 *
 * @param <K> The key-type of the map
 * @param <V> The value-type of the map
 */
public class ObservableMapEvent<K, V> extends ObservableCollectionEvent<V> {
	private final K theOldKey;
	private final K theKey;

	/**
	 * @param elementId The element ID of the entry in the map entry's value collection that was added/removed changed
	 * @param index The index in the entry's value collection of the element that was added/removed/changed
	 * @param type The type of the change (addition/removal/change)
	 * @param move Whether this event represents either the removal of an entry in preparation for a move, or the re-addition of an entry
	 *        that was just removed in the same move operation
	 * @param oldKey The previous key. This will only be different from <code>key</code> if this event represents a modification to a key
	 *        value that does not affect the contents of the key's values. In this case, {@link #getIndex()} will be -1
	 * @param key The key under which a value was added/removed/changed
	 * @param oldValue The value of the element before the change (for change type of {@link CollectionChangeType#set set} only)
	 * @param newValue The value of the element after the change
	 * @param cause The cause of the change
	 */
	public ObservableMapEvent(ElementId elementId, int index, CollectionChangeType type,
		boolean move, K oldKey, K key, V oldValue, V newValue, Object cause) {
		super(elementId, index, type, move, oldValue, newValue, cause);
		theOldKey = oldKey;
		theKey = key;
	}

	@Override
	protected void checkIndex(int index) {
	}

	/** @return The previous key for the entry which was added/removed/changed */
	public K getOldKey() {
		return theOldKey;
	}

	/** @return The key for the entry which was added/removed/changed */
	public K getKey() {
		return theKey;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder().append('[').append(getElementId()).append(':').append(getKey()).append("]: ");
		switch (getType()) {
		case add:
			str.append("+:").append(getNewValue());
			break;
		case remove:
			str.append("-:").append(getOldValue());
			break;
		case set:
			str.append(':').append(getOldValue()).append("->").append(getNewValue());
			break;
		}
		return str.toString();
	}
}
