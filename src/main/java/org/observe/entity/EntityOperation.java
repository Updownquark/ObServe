package org.observe.entity;

import org.qommons.collect.QuickSet.QuickMap;

/**
 * An operation to query, create, update, or delete entities of a particular type from an entity set
 * 
 * @param <E> The type of entity to operate on
 */
public interface EntityOperation<E> {
	/** @return The type of entity that this operation is for */
	ObservableEntityType<E> getEntityType();

	/** @return Any variables that must be fulfuilled in this operation */
	QuickMap<String, EntityOperationVariable<E>> getVariables();
}
