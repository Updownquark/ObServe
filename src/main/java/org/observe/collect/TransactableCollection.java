package org.observe.collect;

import java.util.Collection;

import org.observe.util.Transactable;

/**
 * A collection to which modifications can be batched according to the {@link Transactable} spec.
 * 
 * @param <E> The type of elements in the collection
 */
public interface TransactableCollection<E> extends Collection<E>, Transactable {
}
