package org.observe.assoc;

import org.qommons.Transactable;

/**
 * A multi-map that is transactable
 * 
 * @param <K> The key type for the map
 * @param <V> The value type for the map
 */
public interface TransactableMultiMap<K, V> extends MultiMap<K, V>, Transactable {
}
