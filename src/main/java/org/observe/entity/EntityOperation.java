package org.observe.entity;

import org.qommons.collect.QuickSet.QuickMap;

public interface EntityOperation<E> {
	ObservableEntityType<E> getEntityType();

	QuickMap<String, EntityOperationVariable<E>> getVariables();
}
