package org.observe.assoc;

import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.util.TypeTokens;
import org.qommons.collect.ElementId;

import com.google.common.reflect.TypeToken;

/**
 * An event representing a change to a {@link ObservableMultiMap} or {@link ObservableMap}
 *
 * @param <K> The key-type of the map
 * @param <V> The value-type of the map
 */
public class ObservableMapEvent<K, V> extends ObservableCollectionEvent<V> {
	private final ElementId theKeyElement;
	private final K theKey;
	private final int theKeyIndex;

	/**
	 * @param keyElementId The element ID of the entry in the map under which a value was added/removed/changed
	 * @param valueElementId The element ID of the entry in the map entry's value collection that was added/removed changed
	 * @param keyType The key type of the map
	 * @param valueType The value type of the map
	 * @param keyIndex The index in the key set of the key under which a value was added/removed/changed
	 * @param valueIndex The index in the entry's value collection of the element that was added/removed/changed
	 * @param type The type of the change (addition/removal/change)
	 * @param key The key under which a value was added/removed/changed
	 * @param oldValue The value of the element before the change (for change type of {@link CollectionChangeType#set set} only)
	 * @param newValue The value of the element after the change
	 * @param cause The cause of the change
	 */
	public ObservableMapEvent(ElementId keyElementId, ElementId valueElementId, TypeToken<K> keyType, TypeToken<V> valueType, int keyIndex,
		int valueIndex, CollectionChangeType type, K key, V oldValue, V newValue, Object cause) {
		super(valueElementId, valueType, valueIndex, type, oldValue, newValue, cause);
		theKeyElement = keyElementId;
		theKey = TypeTokens.get().cast(keyType, key);
		theKeyIndex = keyIndex;
	}

	/** @return The element ID of the entry in the map under which a value was added/removed/changed */
	public ElementId getKeyElement() {
		return theKeyElement;
	}

	/** @return The key under which a value was added/removed/changed */
	public K getKey() {
		return theKey;
	}

	/** @return The index in the key set of the key under which a value was added/removed/changed */
	public int getKeyIndex() {
		return theKeyIndex;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder().append('[').append(getElementId()).append(':').append(theKey).append("]: ");
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
