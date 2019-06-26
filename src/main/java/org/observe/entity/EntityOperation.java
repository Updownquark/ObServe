package org.observe.entity;

import org.qommons.collect.ParameterSet.ParameterMap;

public interface EntityOperation<E> {
	ObservableEntityType<E> getEntityType();

	PreparedOperation<E> prepare() throws IllegalStateException, EntityOperationException;

	ParameterMap<EntityOperationVariable<E, ?>> getVariables();
}
