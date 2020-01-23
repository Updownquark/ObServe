package org.observe.entity;

/**
 * A {@link FieldConstraint} that needs no extra information
 *
 * @param <E> The entity type the constraint applies to
 * @param <F> The type of the field the constraint applies to
 */
public class SimpleFieldConstraint<E, F> implements FieldConstraint<E, F> {
	private final ObservableEntityFieldType<E, F> theField;
	private final String theName;
	private final String theConstraintType;

	/**
	 * @param field The field that the constraint applies to
	 * @param name The name of the constraint
	 * @param constraintType The type of the constraint
	 */
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
