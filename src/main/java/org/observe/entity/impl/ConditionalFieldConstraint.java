package org.observe.entity.impl;

import org.observe.entity.ConditionConstraint;
import org.observe.entity.EntityCondition;
import org.observe.entity.FieldConstraint;
import org.observe.entity.ObservableEntityFieldType;

public class ConditionalFieldConstraint<E, F> extends ConditionConstraint<E> implements FieldConstraint<E, F> {
	private ObservableEntityFieldType<E, F> theField;

	ConditionalFieldConstraint(ObservableEntityFieldType<E, F> field, String name, EntityCondition<E> condition) {
		super(field.getEntityType(), name, condition);
		theField = field;
	}

	@Override
	public ObservableEntityFieldType<E, F> getField() {
		return theField;
	}
}
