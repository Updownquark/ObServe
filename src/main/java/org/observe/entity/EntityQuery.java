package org.observe.entity;

import java.util.List;

import org.observe.ObservableValue;
import org.observe.collect.ObservableSortedSet;
import org.qommons.collect.QuickSet.QuickMap;

/**
 * An operation to retrieve a set of entities from an entity set
 *
 * @param <E> The type of entities to retrieve
 */
public interface EntityQuery<E> extends EntitySetOperation<E> {
	/** @return For each field, an enum detailing how the field will be loaded when returned from the query */
	QuickMap<String, FieldLoadType> getFieldLoadTypes();
	/** @return How the query results will be ordered */
	List<QueryOrder<E, ?>> getOrder();

	/**
	 * @param field The field to change the load type of
	 * @param type How the field should be loaded when returned from the query
	 * @return A new query object with the given change made
	 */
	EntityQuery<E> loadField(ObservableEntityFieldType<E, ?> field, FieldLoadType type);
	/**
	 * @param type How all fields should be loaded when returned from the query
	 * @return A new query object with the given change made
	 */
	EntityQuery<E> loadAllFields(FieldLoadType type);

	/**
	 * @return A value representing the number of entities in the entity set matching this query's {@link #getSelection() selection}
	 * @throws IllegalStateException If this operation contains variables that are not fulfilled
	 * @throws EntityOperationException If the query cannot be performed
	 */
	ObservableValue<Long> count() throws IllegalStateException, EntityOperationException;
	/**
	 * @param withUpdates Whether field changes to entities in the results should cause update events in the collection
	 * @return An observable set containing all entities matching this query's {@link #getSelection() selection}
	 * @throws IllegalStateException If this operation contains variables that are not fulfilled
	 * @throws EntityOperationException If the query cannot be performed
	 */
	ObservableSortedSet<E> collect(boolean withUpdates) throws IllegalStateException, EntityOperationException;
	/**
	 * Same as {@link #collect(boolean)}, but returns the values as {@link ObservableEntity} instances.
	 *
	 * @param withUpdates Whether field changes to entities in the results should cause update events in the collection
	 * @return An observable set containing all entities matching this query's {@link #getSelection() selection}
	 * @throws IllegalStateException If this operation contains variables that are not fulfilled
	 * @throws EntityOperationException If the query cannot be performed
	 */
	ObservableSortedSet<ObservableEntity<? extends E>> collectObservable(boolean withUpdates)
		throws IllegalStateException, EntityOperationException;
	/**
	 * @return An observable set containing the identieis of all entities matching this query's {@link #getSelection() selection}
	 * @throws IllegalStateException If this operation contains variables that are not fulfilled
	 * @throws EntityOperationException If the query cannot be performed
	 */
	ObservableSortedSet<EntityIdentity<? super E>> collectIdentities() throws IllegalStateException, EntityOperationException;
}
