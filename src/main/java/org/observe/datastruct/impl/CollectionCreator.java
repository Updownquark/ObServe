package org.observe.datastruct.impl;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.ObservableValue;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.qommons.Transactable;

import com.google.common.reflect.TypeToken;

/**
 * Creates a collection, given the internals. This interface allows creation of observable structures that use observable collections in
 * their internals with specific implementations of those collections. For example, instead of needing separate classes to implement a hash
 * map and a sorted map, the user can use {@link ObservableMapImpl} and supply the {@link org.observe.collect.impl.ObservableHashSet} or
 * {@link org.observe.collect.impl.ObservableTreeSet} constructor to the {@link ObservableMapImpl} constructor.
 *
 * @param <T> The type of elements for the collection
 * @param <C> The sub-type of collection to create
 */
public interface CollectionCreator<T, C extends ObservableCollection<T>> {
	/**
	 * Creates the collection
	 *
	 * @param type The type of elements for the collection
	 * @param lock The lock for the collection to use
	 * @param session The session for the collection to use (see {@link ObservableCollection#getSession()})
	 * @param sessionController The controller for the session. May be null, in which case the transactional methods in this collection will
	 *            not actually create transactions.
	 * @return The new collection
	 */
	C create(TypeToken<T> type, ReentrantReadWriteLock lock, ObservableValue<CollectionSession> session, Transactable sessionController);
}
