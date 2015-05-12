package org.observe.datastruct;

import org.observe.ObservableValue;
import org.observe.collect.ObservableList;

/**
 * A observable tree structure, with a value and a list of trees underneath it. Also functions as a tree node.
 *
 * @param <E> The type of value stored in this tree
 */
public interface ObservableTree<E> extends ObservableValue<E> {
	/** @return The list of tree structures that are the direct children of this tree node */
	ObservableList<? extends ObservableTree<?>> getChildren();
}
