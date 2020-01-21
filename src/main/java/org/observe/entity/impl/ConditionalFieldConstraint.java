package org.observe.entity.impl;

import org.observe.entity.ConditionConstraint;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityCondition.LiteralCondition;
import org.observe.entity.FieldConstraint;
import org.observe.entity.ObservableEntityFieldType;

public class ConditionalFieldConstraint<E, F> extends ConditionConstraint<E> implements FieldConstraint<E, F> {
	private ObservableEntityFieldType<E, F> theField;

	ConditionalFieldConstraint(ObservableEntityFieldType<E, F> field, String name, EntityCondition.LiteralCondition<E, F> condition) {
		super(field.getEntityType(), name, condition);
		theField = field;
	}

	@Override
	public ObservableEntityFieldType<E, F> getField() {
		return theField;
	}

	@Override
	public EntityCondition.LiteralCondition<E, F> getCondition() {
		return (LiteralCondition<E, F>) super.getCondition();
	}

	@Override
	public String canAccept(F value) {
		return getCondition().test(value) ? null : ("CONSTRAINT VIOLATION: " + getCondition());
	}
}
