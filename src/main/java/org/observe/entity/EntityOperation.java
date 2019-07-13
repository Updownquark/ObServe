package org.observe.entity;

import org.qommons.collect.QuickSet.QuickMap;

public interface EntityOperation<E> {
	ObservableEntityType<E> getEntityType();

	PreparedOperation<E> prepare() throws IllegalStateException, EntityOperationException;

	QuickMap<String, EntityOperationVariable<E, ?>> getVariables();
}
