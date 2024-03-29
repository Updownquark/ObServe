package org.observe.entity;

/**
 * A {@link EntityConstraint#CHECK check}-type entity constraint that only checks the value of a single field
 *
 * @param <E> The entity type that the constraint applies to
 * @param <F> The type of the field the constraint applies to
 */
public class ConditionalFieldConstraint<E, F> extends ConditionConstraint<E> implements FieldConstraint<E, F> {
	private ObservableEntityFieldType<E, F> theField;

	/**
	 * @param field The field that the constraint applies to
	 * @param name The name of the constraint
	 * @param condition The condition that values of the field must pass
	 */
	public ConditionalFieldConstraint(ObservableEntityFieldType<E, F> field, String name,
		EntityCondition.LiteralCondition<E, F> condition) {
		super(field.getOwnerType(), name, condition);
		theField = field;
	}

	@Override
	public ObservableEntityFieldType<E, F> getField() {
		return theField;
	}

	@Override
	public EntityCondition.LiteralCondition<E, F> getCondition() {
		return (EntityCondition.LiteralCondition<E, F>) super.getCondition();
	}

	@Override
	public String canAccept(F value) {
		return getCondition().test(value) ? null : ("CONSTRAINT VIOLATION: " + getCondition());
	}
}
