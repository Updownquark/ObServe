package org.observe.entity.impl;

import org.observe.entity.FieldConstraint;
import org.observe.entity.ObservableEntityFieldType;

public class SimpleFieldConstraint<E, F> implements FieldConstraint<E, F> {
	private final ObservableEntityFieldType<E, F> theField;
	private final String theName;
	private final String theConstraintType;

	public SimpleFieldConstraint(ObservableEntityFieldType<E, F> field, String name, String constraintType) {
		theField = field;
		theName = name;
		theConstraintType = constraintType;
	}

	@Override
	public ObservableEntityFieldType<E, F> getField() {
		return theField;
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public String getConstraintType() {
		return theConstraintType;
	}

	@Override
	public String canAccept(F value) {
		switch (theConstraintType) {
		case NOT_NULL:
			return value != null ? null : ("CONSTRAINT VIOLATION: " + theField + " NOT NULL");
		case UNIQUE:
			return null; // Can't determine this in isolation
		default:
			throw new IllegalStateException("Unrecognized constraint type: " + theConstraintType);
		}
	}
}
