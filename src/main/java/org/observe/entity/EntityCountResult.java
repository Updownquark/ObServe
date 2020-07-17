package org.observe.entity;

import org.observe.ObservableValue;

/**
 * A result of an {@link EntityQuery}'s {@link EntityQuery#count()} method.
 *
 * @param <E> The type of the entities counted in the result
 */
public interface EntityCountResult<E> extends EntityQueryResult<E>, ObservableValue<Long> {
	@Override
	default EntityCountResult<E> waitFor() throws InterruptedException, EntityOperationException {
		EntityQueryResult.super.waitFor();
		return this;
	}

	@Override
	default EntityCountResult<E> waitFor(long timeout, int nanos) throws InterruptedException, EntityOperationException {
		EntityQueryResult.super.waitFor(timeout, nanos);
		return this;
	}
}
