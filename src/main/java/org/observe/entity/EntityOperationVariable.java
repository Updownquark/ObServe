package org.observe.entity;

public class EntityOperationVariable<E, T> {
	private final EntityOperation<E> theOperation;
	private final String theName;
	private final EntityValueAccess<? super E, T> theValue;

	public EntityOperationVariable(EntityOperation<E> operation, String name, EntityValueAccess<? super E, T> value) {
		theOperation = operation;
		theName = name;
		theValue = value;
	}

	public EntityOperation<E> getOperation() {
		return theOperation;
	}

	public String getName() {
		return theName;
	}

	public EntityValueAccess<? super E, T> getValue() {
		return theValue;
	}
}
