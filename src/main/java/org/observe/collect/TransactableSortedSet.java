package org.observe.collect;

import java.util.NavigableSet;

/**
 * A sorted set that is transactable
 *
 * @param <E> The type of elements in the set
 */
public interface TransactableSortedSet<E> extends NavigableSet<E>, TransactableSet<E> {
}
