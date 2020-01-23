package org.observe.entity;

import java.util.List;
import java.util.function.Function;

import org.qommons.Named;
import org.qommons.collect.QuickSet.QuickMap;

/**
 * A type of entity in an {@link ObservableEntityDataSet entity set}
 *
 * @param <E> The java type of the entity
 */
public interface ObservableEntityType<E> extends Named {
	/** @return The entity set that this type belongs to */
	ObservableEntityDataSet getEntitySet();

	/** @return Any other types that this type inherits from */
	List<? extends ObservableEntityType<? super E>> getSupers();

	/** @return The java type associated with this entity type, if any */
	Class<E> getEntityType();

	/** @return This entity's field types */
	QuickMap<String, ObservableEntityFieldType<E, ?>> getFields();
	/** @return This entity's identity field types */
	QuickMap<String, ObservableEntityFieldType<E, ?>> getIdentityFields();

	/**
	 * @param id The identity of the entity to get
	 * @return The entity of this type (or one of its sub-types) with the given identity, or null if there is none such in the entity set
	 */
	ObservableEntity<? extends E> observableEntity(EntityIdentity<? super E> id);
	/**
	 * @param entity The entity object queried from this type
	 * @return The observable entity represented by the given entity object
	 */
	ObservableEntity<? extends E> observableEntity(E entity);

	/**
	 * @return A selection object (defaulted to ALL) which may be used to query, update, or delete existing entities of this type from the
	 *         entity set
	 */
	EntitySelection<E> select();
	/** @return An entity creator which may be used to create new instances of this type in the entity set */
	EntityCreator<E> create();

	/**
	 * @param fieldGetter The getter for the field in the java type
	 * @return The field type in this entity type represented by the given java field
	 * @throws IllegalArgumentException If the given field does not represent a field getter in this entity type
	 */
	<F> ObservableEntityFieldType<E, F> getField(Function<? super E, F> fieldGetter) throws IllegalArgumentException;

	/** @return A list of constraints that must be obeyed by all instances of this type in the entity set */
	List<EntityConstraint<E>> getConstraints();

	/** @return A builder to build an {@link EntityIdentity} of this type */
	default EntityIdentity.Builder<E> buildId() {
		return EntityIdentity.build(this);
	}
}
