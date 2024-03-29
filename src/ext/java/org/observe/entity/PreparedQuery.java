package org.observe.entity;

import java.util.List;

/**
 * A prepared {@link EntityQuery}
 * 
 * @param <E> The type of entity to query
 */
public interface PreparedQuery<E> extends PreparedOperation<E>, EntityQuery<E> {
	@Override
	default ObservableEntityType<E> getEntityType() {
		return PreparedOperation.super.getEntityType();
	}

	@Override
	ConfigurableQuery<E> getDefinition();

	@Override
	default List<QueryOrder<E, ?>> getOrder() {
		return getDefinition().getOrder();
	}
}
