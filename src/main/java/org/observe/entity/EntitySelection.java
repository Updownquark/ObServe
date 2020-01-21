package org.observe.entity;

import java.util.function.Function;

public interface EntitySelection<E> {
	default ObservableEntityType<E> getEntityType() {
		return getCondition().getEntityType();
	}

	EntityCondition<E> getCondition();

	EntitySelection<E> where(Function<EntityCondition<E>, EntityCondition<E>> condition);

	EntityQuery<E> query();
	EntityUpdate<E> update();
	EntityDeletion<E> delete();
}
