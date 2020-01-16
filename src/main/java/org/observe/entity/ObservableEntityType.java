package org.observe.entity;

import java.util.List;
import java.util.function.Function;

import org.qommons.collect.QuickSet.QuickMap;

public interface ObservableEntityType<E> {
	List<? extends ObservableEntityType<? super E>> getSupers();
	String getEntityName();
	Class<E> getEntityType();

	QuickMap<String, ObservableEntityFieldType<E, ?>> getFields();
	QuickMap<String, IdentityFieldType<E, ?>> getIdentityFields();

	ObservableEntity<? extends E> observableEntity(EntityIdentity<? super E> id);
	ObservableEntity<? extends E> observableEntity(E entity);

	EntitySelection<E> select();
	EntityCreator<E> create();

	<F> ObservableEntityFieldType<E, F> getField(Function<? super E, F> fieldGetter);

	List<EntityConstraint<E>> getConstraints();
}
