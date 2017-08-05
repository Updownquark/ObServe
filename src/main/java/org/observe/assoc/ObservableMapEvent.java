package org.observe.assoc;

import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollectionEvent;
import org.qommons.collect.ElementId;

import com.google.common.reflect.TypeToken;

/**
 * An event representing a change to a {@link ObservableMultiMap} or {@link ObservableMap}
 * 
 * @param <K> The key-type of th map
 * @param <V> The value-type of the map
 */
public class ObservableMapEvent<K, V> extends ObservableCollectionEvent<V> {
	private final ElementId theKeyElement;
	private final K theKey;
	private final int theKeyIndex;

	public ObservableMapEvent(ElementId keyElementId, ElementId valueElementId, TypeToken<K> keyType, TypeToken<V> valueType, int keyIndex,
		int valueIndex, CollectionChangeType type, K key, V oldValue, V newValue, Object cause) {
		super(valueElementId, valueType, valueIndex, type, oldValue, newValue, cause);
		theKeyElement = keyElementId;
		theKey = (K) keyType.wrap().getRawType().cast(key);
		theKeyIndex = keyIndex;
	}

	public ElementId getKeyElement() {
		return theKeyElement;
	}

	public K getKey() {
		return theKey;
	}

	public int getKeyIndex() {
		return theKeyIndex;
	}
}
