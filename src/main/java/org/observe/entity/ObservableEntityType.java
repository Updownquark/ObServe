package org.observe.entity;

import java.util.List;
import java.util.function.Function;

import org.observe.config.ConfiguredValueType;
import org.observe.entity.EntityIdentity.Builder;
import org.qommons.Named;
import org.qommons.collect.QuickSet.QuickMap;

/**
 * A type of entity in an {@link ObservableEntityDataSet entity set}
 *
 * @param <E> The java type of the entity
 */
public interface ObservableEntityType<E> extends ConfiguredValueType<E>, Named {
	/** @return The entity set that this type belongs to */
	ObservableEntityDataSet getEntitySet();

	@Override
	List<? extends ObservableEntityType<? super E>> getSupers();

	/** @return The java type associated with this entity type, if any */
	Class<E> getEntityType();

	@Override
	QuickMap<String, ? extends ObservableEntityFieldType<E, ?>> getFields();
	/** @return This entity's identity field types */
	QuickMap<String, ? extends ObservableEntityFieldType<E, ?>> getIdentityFields();

	@Override
	<F> ObservableEntityFieldType<E, F> getField(Function<? super E, F> fieldGetter) throws IllegalArgumentException;

	/**
	 * @param id The identity of the entity to get
	 * @return The entity of this type (or one of its sub-types) with the given identity, or null if there is none such in the entity set
	 * @throws EntityOperationException If the operation cannot be performed
	 */
	ObservableEntity<? extends E> observableEntity(EntityIdentity<E> id) throws EntityOperationException;
	/**
	 * @param entity The entity object queried from this type
	 * @return The observable entity represented by the given entity object
	 */
	ObservableEntity<? extends E> observableEntity(E entity);

	/**
	 * @return A selection object (defaulted to ALL) which may be used to query, update, or delete existing entities of this type from the
	 *         entity set
	 */
	EntityCondition.All<E> select();
	/** @return An entity creator which may be used to create new instances of this type in the entity set */
	ConfigurableCreator<E> create();

	/** @return A list of constraints that must be obeyed by all instances of this type in the entity set */
	List<EntityConstraint<E>> getConstraints();

	/**
	 * @param entityType The entity type to test
	 * @return Whether this entity type is a super-type of <code>entityType</code>
	 */
	default boolean isAssignableFrom(ObservableEntityType<?> entityType) {
		return ObservableEntityUtil.isAssignableFrom(this, entityType);
	}

	/** @return A builder to build an {@link EntityIdentity} of this type */
	default EntityIdentity.Builder<E> buildId() {
		return EntityIdentity.build(this);
	}

	/**
	 * @param subId An ID for an entity of this type or a sub-types
	 * @return An ID of this type for the same entity
	 */
	default EntityIdentity<E> fromSubId(EntityIdentity<? extends E> subId) {
		if (subId.getEntityType() == this)
			return (EntityIdentity<E>) subId;
		Builder<E> builder = buildId();
		for (int f = 0; f < getIdentityFields().keySize(); f++) {
			ObservableEntityFieldType<E, Object> field = (ObservableEntityFieldType<E, Object>) getIdentityFields().get(f);
			builder.with(field, subId.getValue(field));
		}
		return builder.build();
	}

	@Override
	default boolean allowsCustomFields() {
		return false;
	}
}
