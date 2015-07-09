package org.observe.datastruct;

import java.util.Map;

import org.observe.util.Transactable;

/**
 * A transactable map
 * 
 * @param <K> The key type for the map
 * @param <V> The value type for the map
 */
public interface TransactableMap<K, V> extends Map<K, V>, Transactable {
}