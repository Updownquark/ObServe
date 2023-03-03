package org.observe.assoc;

import org.observe.collect.CollectionChangeType;
import org.qommons.collect.ElementId;

/**
 * An event representing a change to a {@link ObservableMultiMap}
 *
 * @param <K> The key-type of the map
 * @param <V> The value-type of the map
 */
public class ObservableMultiMapEvent<K, V> extends ObservableMapEvent<K, V> {
	private final ElementId theKeyElement;
	private final int theKeyIndex;

	/**
	 * @param keyElementId The element ID of the entry in the map under which a value was added/removed/changed
	 * @param valueElementId The element ID of the entry in the map entry's value collection that was added/removed changed
	 * @param keyIndex The index in the key set of the key under which a value was added/removed/changed
	 * @param valueIndex The index in the entry's value collection of the element that was added/removed/changed
	 * @param type The type of the change (addition/removal/change)
	 * @param oldKey The previous key. This will only be different from <code>key</code> if this event represents a modification to a key
	 *        value that does not affect the contents of the key's values. In this case, {@link #getIndex()} will be -1 and
	 *        {@link #getElementId()} will be null.
	 * @param key The key under which a value was added/removed/changed
	 * @param oldValue The value of the element before the change (for change type of {@link CollectionChangeType#set set} only)
	 * @param newValue The value of the element after the change
	 * @param causes The causes of the change
	 */
	public ObservableMultiMapEvent(ElementId keyElementId, ElementId valueElementId, int keyIndex, int valueIndex,
		CollectionChangeType type, K oldKey, K key, V oldValue, V newValue, Object... causes) {
		super(valueElementId, valueIndex, type, oldKey, key, oldValue, newValue, causes);
		theKeyElement = keyElementId;
		theKeyIndex = keyIndex;
	}

	/** @return The element ID of the entry in the map under which a value was added/removed/changed */
	public ElementId getKeyElement() {
		return theKeyElement;
	}

	/** @return The key under which a value was added/removed/changed */
	@Override
	public K getKey() {
		return super.getKey();
	}

	/** @return The index in the key set of the key under which a value was added/removed/changed */
	public int getKeyIndex() {
		return theKeyIndex;
	}
}
