package org.observe.entity;

import java.util.function.Function;

public interface EntitySelection<E> extends EntityOperation<E> {
	EntitySelection<E> where(Function<EntityCondition<E>, EntityCondition<E>> condition);

	@Override
	default boolean isPrepared() {
		return false;
	}
	@Override
	default EntityOperation<E> prepare() throws IllegalStateException, EntityOperationException {
		throw new IllegalStateException("This is a precursor to an actual operation and cannot be prepared");
	}
	@Override
	default EntityOperation<E> satisfy(String variableName, Object value) throws IllegalStateException, IllegalArgumentException {
		throw new IllegalStateException("This is a precursor to an actual operation and cannot satisfy variables");
	}

	EntityQuery<E> query();
	EntityUpdate<E> update();
	EntityDeletion<E> delete();
}
