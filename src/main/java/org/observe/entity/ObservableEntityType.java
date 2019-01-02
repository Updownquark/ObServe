package org.observe.entity;

import org.qommons.collect.ParameterSet.ParameterMap;

public interface ObservableEntityType<E> {
	ObservableEntityType<? super E> getParent();

	ParameterMap<ObservableEntityFieldType<? super E, ?>> getFields();
	ParameterMap<IdentityFieldType<? super E, ?>> getIdentityFields();

	ObservableEntity<? extends E> observableEntity(EntityIdentity<? super E> id);
	ObservableEntity<? extends E> observableEntity(E entity);
}
