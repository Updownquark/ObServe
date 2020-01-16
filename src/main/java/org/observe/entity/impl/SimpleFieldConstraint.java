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
}
