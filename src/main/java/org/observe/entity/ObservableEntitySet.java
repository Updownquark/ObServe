package org.observe.entity;

import org.observe.collect.ObservableSortedSet;
import org.observe.config.ObservableValueSet;
import org.observe.util.TypeTokens;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ObservableValueSet} generated by an {@link EntityCollectionResult}
 * 
 * @param <E> The type of entities in this collection
 */
public interface ObservableEntitySet<E> extends ObservableValueSet<E> {
	/** @return The {@link EntityCollectionResult} that this entity set belongs to */
	EntityCollectionResult<E> getResults();

	@Override
	default ObservableEntityType<E> getType() {
		return getResults().getOperation().getEntityType();
	}

	/**
	 * @return This collection, where each entity is represented by its {@link ObservableEntityType#getEntityType() java-type}
	 * @throws IllegalStateException If the entity is not mapped to a java type
	 */
	@Override
	default ObservableSortedSet<? extends E> getValues() {
		Class<E> type = getType().getEntityType();
		if (type == null)
			throw new IllegalStateException("This entity is not mapped to a java type");
		return ((ObservableSortedSet<ObservableEntity<? extends E>>) getEntities()).flow()
			.<E> mapEquivalent(TypeTokens.get().of(type), //
				ObservableEntity::getEntity, //
				e -> getType().observableEntity(e)//
				).collectPassive();
	}

	/**
	 * @return This collection, where each entity is represented as an {@link ObservableEntity} instance
	 * @throws IllegalStateException If the entity is not mapped to a java type
	 */
	ObservableSortedSet<? extends ObservableEntity<? extends E>> getEntities();

	@Override
	default ConfigurableCreator<E, E> create() {
		return (ConfigurableCreator<E, E>) ObservableValueSet.super.create();
	}

	@Override
	<E2 extends E> ConfigurableCreator<E, E2> create(TypeToken<E2> subType);
}
