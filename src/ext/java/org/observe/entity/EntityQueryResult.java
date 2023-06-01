package org.observe.entity;

/**
 * An entity result that keeps its value up-to-date with changes to the entity set.
 *
 * @param <E> The entity type of the result
 * @param <T> The type of the result
 */
public interface EntityQueryResult<E, T> extends ObservableEntityResult<E, T> {
	@Override
	EntityQuery<E> getOperation();
}
