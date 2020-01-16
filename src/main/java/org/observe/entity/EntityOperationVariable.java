package org.observe.entity;

public class EntityOperationVariable<E, T> {
	private final ObservableEntityType<E> theEntityType;
	private final String theName;
	private final EntityValueAccess<? super E, T> theValue;

	public EntityOperationVariable(ObservableEntityType<E> entityType, String name, EntityValueAccess<? super E, T> value) {
		theEntityType = entityType;
		theName = name;
		theValue = value;
	}

	public ObservableEntityType<E> getEntityType() {
		return theEntityType;
	}

	public String getName() {
		return theName;
	}

	public EntityValueAccess<? super E, T> getValue() {
		return theValue;
	}
}
