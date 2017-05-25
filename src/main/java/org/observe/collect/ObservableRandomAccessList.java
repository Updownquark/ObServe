package org.observe.collect;

import java.util.RandomAccess;

/**
 * An ObservableList whose contents can be accessed by index with constant (or near-constant) time
 *
 * @param <E> The type of values stored in the list
 */
public interface ObservableRandomAccessList<E> extends ObservableList<E>, RandomAccess {
}
