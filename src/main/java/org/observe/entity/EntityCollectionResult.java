package org.observe.entity;

import org.observe.collect.ObservableSortedSet;
import org.observe.util.TypeTokens;

/**
 * The result of an {@link EntityQuery}'s {@link EntityQuery#collect(boolean) collect} method. This collection and the one returned from
 * {@link #asSimpleEntities()} will be empty until this result's {@link #getStatus() status} is
 * {@link org.observe.config.ObservableOperationResult.ResultStatus#FULFILLED fulfilled}.
 *
 * @param <E> The type of entity in the result
 */
public interface EntityCollectionResult<E> extends EntityQueryResult<E>, ObservableSortedSet<ObservableEntity<? extends E>> {
	@Override
	EntityQuery<E> getOperation();

	@Override
	EntityCollectionResult<E> dispose();

	@Override
	default EntityCollectionResult<E> waitFor() throws InterruptedException, EntityOperationException {
		EntityQueryResult.super.waitFor();
		return this;
	}

	@Override
	default EntityCollectionResult<E> waitFor(long timeout, int nanos) throws InterruptedException, EntityOperationException {
		EntityQueryResult.super.waitFor(timeout, nanos);
		return this;
	}

	/**
	 * @return This collection, where each entity is represented by its {@link ObservableEntityType#getEntityType() java-type}
	 * @throws IllegalStateException If the entity is not mapped to a java type
	 */
	default ObservableSortedSet<E> asSimpleEntities() throws IllegalStateException {
		Class<E> type = getOperation().getEntityType().getEntityType();
		if (type == null)
			throw new IllegalStateException("This entity is not mapped to a java type");
		return flow().mapEquivalent(TypeTokens.get().of(type), //
			ObservableEntity::getEntity, //
			e -> getOperation().getEntityType().observableEntity(e)//
			).collectPassive();
	}
}
