package org.observe.entity;

import org.qommons.Named;

/**
 * Represents a condition that must be met by all instances of an entity type in an entity set
 *
 * @param <E> The entity type the condition applies to
 */
public interface EntityConstraint<E> extends Named {
	/** A non-null constraint on an entity's fields */
	public static final String NOT_NULL="not null";
	/** A unique constraint among the values of a field of all instances of an entity type */
	public static final String UNIQUE="unique";
	/** A condition constraint for the values of one or more fields in the entity */
	public static final String CHECK="check";

	/** @return The entity type this condition applies to */
	ObservableEntityType<E> getEntityType();

	/** @return The type of the constraint */
	String getConstraintType();
}
