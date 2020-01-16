package org.observe.entity;

import org.qommons.Named;

public interface EntityConstraint<E> extends Named {
	public static final String NOT_NULL="not null";
	public static final String UNIQUE="unique";
	public static final String CHECK="check";

	ObservableEntityType<E> getEntityType();
	String getConstraintType();
}
