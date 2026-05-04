package org.observe.data;

import org.observe.config.SyncValueSet;
import org.qommons.Stamped;
import org.qommons.Transactable;

/** A data source that supplies {@link SyncValueSet}s of entities by type */
public interface ObservableEntityDataSource extends Transactable, Stamped {
	/**
	 * @param <E> The compile-time type to get the entity type for
	 * @param type The run-time java type to get the entity type for
	 * @return The entity type for the given java type
	 */
	<E> ReflectedEntityValueType<E> getType(Class<E> type);

	/**
	 * @param <E> The compile-time type to get the entity set for
	 * @param type The run-time type to get the entity set for
	 * @return The value set of entities in this data set, with creation capability
	 * @throws IllegalArgumentException If the given type is not supported by this data source
	 */
	<E> SyncValueSet<E> observeEntities(Class<E> type) throws IllegalArgumentException;
}
