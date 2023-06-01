package org.observe.entity;

/**
 * Specifies sorting for a {@link EntityQuery}'s results
 * 
 * @param <E> The entity type of the query
 * @param <F> The type of the attribute to order the results by
 */
public class QueryOrder<E, F> {
	private final EntityValueAccess<E, F> theValue;
	private final boolean isAscending;

	/**
	 * @param value The attribute by which to order the query results
	 * @param ascending Whether to order the results smallest-to-greatest or greatest-to-smallest
	 */
	public QueryOrder(EntityValueAccess<E, F> value, boolean ascending) {
		theValue = value;
		isAscending = ascending;
	}

	/** @return The attribute by which query results are ordered */
	public EntityValueAccess<E, F> getValue() {
		return theValue;
	}

	/** @return Whether the query results are ordered by the given value in smallest-to-greatest or greatest-to-smallest order */
	public boolean isAscending() {
		return isAscending;
	}
}
