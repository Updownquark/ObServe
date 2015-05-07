package org.observe.collect;

/**
 * An ObservableCollection whose contents are hashed or stored by some other mechanism that allows very fast access by value
 * 
 * @param <E> The type of values store in this collection
 */
public interface ObservableFastFindCollection<E> extends ObservableCollection<E> {
}
