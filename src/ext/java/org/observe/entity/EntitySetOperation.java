package org.observe.entity;

/**
 * An operation on a set of existing entities in an entity set
 * 
 * @param <E> The type of entity to operate on
 */
public interface EntitySetOperation<E> extends EntityOperation<E> {
	/** @return The selection determining what entities in the set to operate on */
	EntityCondition<E> getSelection();

	@Override
	default ObservableEntityType<E> getEntityType() {
		return getSelection().getEntityType();
	}
}
