package org.observe.entity;

public class ConditionConstraint<E> implements EntityConstraint<E> {
	private final ObservableEntityType<E> theEntityType;
	private final String theName;
	private final EntityCondition<E> theCondition;

	public ConditionConstraint(ObservableEntityType<E> entityType, String name, EntityCondition<E> condition) {
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

	public EntityCondition<E> getCondition() {
		return theCondition;
	}

	@Override
	public String getConstraintType() {
		return CHECK;
	}
}
