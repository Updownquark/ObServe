package org.observe.collect;

import java.util.List;

import org.observe.util.Transactable;

/**
 * A list to which modifications can be batched according to the {@link Transactable} spec.
 *
 * @param <E> The type of elements in the list
 */
public interface TransactableList<E> extends TransactableCollection<E>, List<E> {
}
