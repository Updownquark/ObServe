package org.observe.entity;

public class QueryOrder<E, F> {
	private final EntityValueAccess<E, F> theValue;
	private final boolean isAscending;

	public QueryOrder(EntityValueAccess<E, F> value, boolean ascending) {
		theValue = value;
		isAscending = ascending;
	}

	public EntityValueAccess<E, F> getValue() {
		return theValue;
	}

	public boolean isAscending() {
		return isAscending;
	}
}
