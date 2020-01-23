package org.observe.entity;

import org.qommons.collect.QuickSet.QuickMap;

/**
 * An operation to modify fields in a set of entities in an entity set
 * 
 * @param <E> The type of entity to update
 */
public interface EntityUpdate<E> extends EntityModification<E> {
	/** The value of a field in {@link #getFieldValues()} that is not to be modified by this operation */
	static Object NOT_SET = new Object() {
		@Override
		public String toString() {
			return "Not Set";
		}
	};

	/** @return The values to set */
	QuickMap<String, Object> getFieldValues();
}
