package org.observe.entity;

import java.util.function.Function;

/**
 * A condition on a type of entity, based on which an operation ({@link #query() query}, {@link #update() update}, or {@link #delete()
 * delete}) can be performed
 *
 * @param <E> The type of entity being selected
 */
public interface EntitySelection<E> {
	/** @return The entity type being selected */
	default ObservableEntityType<E> getEntityType() {
		return getCondition().getEntityType();
	}

	/** @return The condition determining what entities the operation will affect */
	EntityCondition<E> getCondition();

	/**
	 * @param condition Creates a condition for the selection
	 * @return A new selection with the given condition
	 */
	EntitySelection<E> where(Function<EntityCondition<E>, EntityCondition<E>> condition);

	/** @return A query to retrieve entities matching this selection's {@link #getCondition() condition} */
	EntityQuery<E> query();
	/** @return An update operation to change fields of entities matching this selection's {@link #getCondition() condition} */
	EntityUpdate<E> update();
	/** @return A delete operation to remove entities matching this selection's {@link #getCondition() condition} from the entity set */
	EntityDeletion<E> delete();
}
