package org.observe.entity;

/**
 * An {@link EntityConstraint} that applies to a particular field
 * 
 * @param <E> The entity type the constraint applies to
 * @param <F> The type of the field the constraint applies to
 */
public interface FieldConstraint<E, F> extends EntityConstraint<E> {
	/** @return The field that the constraint applies to */
	ObservableEntityFieldType<E, F> getField();

	@Override
	default ObservableEntityType<E> getEntityType() {
		return getField().getOwnerType();
	}

	/**
	 * @param value The value to test
	 * @return A message detailing how the given value would violate this constraint, or null if it doesn't
	 */
	String canAccept(F value);
}
