package org.observe.entity;

/**
 * A {@link EntityConstraint#CHECK check-}type constraint on an entity
 * 
 * @param <E> The type of the entity the constraint applies to
 */
public class ConditionConstraint<E> implements EntityConstraint<E> {
	private final ObservableEntityType<E> theEntityType;
	private final String theName;
	private final EntitySelection<E> theCondition;

	/**
	 * @param entityType The entity type the constraint applies to
	 * @param name The name of the constraint
	 * @param condition The condition that must be obeyed by all entities of the given type in the entity set
	 */
	public ConditionConstraint(ObservableEntityType<E> entityType, String name, EntitySelection<E> condition) {
		theEntityType = entityType;
		theName = name;
		theCondition = condition;
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public ObservableEntityType<E> getEntityType() {
		return theEntityType;
	}

	/** @return The condition that must be obeyed by all entities of the constraint's type in the entity set */
	public EntitySelection<E> getCondition() {
		return theCondition;
	}

	@Override
	public String getConstraintType() {
		return CHECK;
	}
}
