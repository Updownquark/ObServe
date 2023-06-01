package org.observe.entity;

import java.util.List;

import org.observe.Observable;
import org.observe.entity.impl.ObservableEntityDataSetImpl;
import org.qommons.Transactable;

/** A set of entities */
public interface ObservableEntityDataSet extends Transactable {
	/** @return All entity types in this entity set */
	List<ObservableEntityType<?>> getEntityTypes();

	/**
	 * @param entityName The name of the entity to retrieve
	 * @return The entity type with the given name in this entity set, or null if there is none such
	 */
	ObservableEntityType<?> getEntityType(String entityName);

	/**
	 * @param type The java type to retrieve the entity type for
	 * @return The entity type mapped to the given java type in this entity set, or null if there is none such
	 */
	<E> ObservableEntityType<E> getEntityType(Class<E> type);

	/**
	 * @param entity The entity queried from one of the types in this entity set
	 * @return The observable entity representing the given entity object
	 * @throws IllegalArgumentException If the given entity was not queried from this entity set
	 */
	default <E, X extends E> ObservableEntity<? extends E> observableEntity(E entity) throws IllegalArgumentException {
		ObservableEntityType<X> type = getEntityType((Class<X>) entity.getClass());
		if (type == null)
			throw new IllegalArgumentException("Entity type " + entity.getClass().getName() + " not supported");
		return type.observableEntity((X) entity);
	}

	/** @return An observable that fires a change whenever the entity data is changed */
	Observable<List<EntityChange<?>>> changes();

	/**
	 * @param implementation The data set implementation to power the entity set
	 * @return A builder for an entity set
	 */
	static ObservableEntityDataSetImpl.EntitySetBuilder build(ObservableEntityProvider implementation) {
		return ObservableEntityDataSetImpl.build(implementation);
	}
}
