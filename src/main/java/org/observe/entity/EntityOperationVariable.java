package org.observe.entity;

public class EntityOperationVariable<E, T> {
	private final EntityOperation<E> theOperation;
	private final String theName;
	private final ObservableEntityFieldType<?, T> theField;

	public EntityOperationVariable(EntityOperation<E> operation, String name, ObservableEntityFieldType<?, T> field) {
		theOperation = operation;
		theName = name;
		theField = field;
	}

	public EntityOperation<E> getOperation() {
		return theOperation;
	}

	public String getName() {
		return theName;
	}

	public ObservableEntityFieldType<?, T> getField() {
		return theField;
	}
}
