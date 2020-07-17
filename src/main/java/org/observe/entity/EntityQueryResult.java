package org.observe.entity;

/**
 * An entity result that keeps its value up-to-date with changes to the entity set.
 *
 * @param <E> The entity type of the result
 */
public interface EntityQueryResult<E> extends ObservableEntityResult<E> {
	@Override
	EntityQuery<E> getOperation();
}
