package org.observe.entity;

import java.util.function.Function;

public interface EntitySelection<E> extends EntityOperation<E> {
	EntitySelection<E> where(Function<EntityCondition<E>, EntityCondition<E>> condition);

	@Override
	default PreparedOperation<E> prepare() throws IllegalStateException, EntityOperationException {
		throw new IllegalStateException("This is a precursor to an actual operation and cannot be prepared");
	}

	EntityQuery<E> query();
	EntityUpdate<E> update();
	EntityDeletion<E> delete();
}
