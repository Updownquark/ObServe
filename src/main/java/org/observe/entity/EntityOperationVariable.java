package org.observe.entity;

public class EntityOperationVariable<E> {
	private final ObservableEntityType<E> theEntityType;
	private final String theName;

	public EntityOperationVariable(ObservableEntityType<E> entityType, String name) {
		theEntityType = entityType;
		theName = name;
	}

	public ObservableEntityType<E> getEntityType() {
		return theEntityType;
	}

	public String getName() {
		return theName;
	}
}
