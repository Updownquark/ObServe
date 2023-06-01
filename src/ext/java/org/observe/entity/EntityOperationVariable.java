package org.observe.entity;

import org.qommons.Named;

/**
 * A variable in an {@link EntityOperation}
 *
 * @param <E> The entity type of the operation
 */
public class EntityOperationVariable<E> implements Named {
	private final ObservableEntityType<E> theEntityType;
	private final String theName;

	/**
	 * @param entityType The entity type of the operation
	 * @param name The name of the variable
	 */
	public EntityOperationVariable(ObservableEntityType<E> entityType, String name) {
		theEntityType = entityType;
		theName = name;
	}

	/** @return The entity type of the operation */
	public ObservableEntityType<E> getEntityType() {
		return theEntityType;
	}

	/** @return The name of the variable */
	@Override
	public String getName() {
		return theName;
	}

	@Override
	public String toString() {
		return theName;
	}
}
