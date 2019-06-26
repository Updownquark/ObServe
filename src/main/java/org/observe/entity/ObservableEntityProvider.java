package org.observe.entity;

import org.observe.Observable;
import org.qommons.Transactable;

/**
 * <p>
 * An implementation data source to provide an ObservableEntitySet with entity data and possibly the power to execute changes to the data
 * set.
 * </p>
 * <p>
 * The Transactable interface does not necessarily allow the locking of the data source to the exclusion of other users of it. It only means
 * that no {@link #changes() changes} will fire while it is locked. Changes occurring in the data source during a lock hold will be fired
 * when the lock is released.
 * </p>
 */
public interface ObservableEntityProvider extends Transactable {
	Object prepare(EntityOperation<?> operation) throws EntityOperationException;

	<E> EntityIdentity<E> create(EntityCreator<E> creator, Object prepared) throws EntityOperationException;
	<E> GenericPersistedEntity<E> createAndGet(EntityCreator<E> creator, Object prepared) throws EntityOperationException;

	long count(EntityQuery<?> query, Object prepared) throws EntityOperationException;
	<E> Iterable<GenericPersistedEntity<E>> query(EntityQuery<E> query, Object prepared) throws EntityOperationException;

	long update(EntityUpdate<?> update, Object prepared) throws EntityOperationException;

	long delete(EntityDeletion<?> delete, Object prepared) throws EntityOperationException;

	/** @return An observable that fires a change whenever the entity data is changed externally */
	Observable<EntityChange<?>> changes();
}
