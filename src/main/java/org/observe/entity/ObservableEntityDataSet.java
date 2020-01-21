package org.observe.entity;

import java.util.List;

import org.qommons.Transactable;

public interface ObservableEntityDataSet extends Transactable {
	List<ObservableEntityType<?>> getEntityTypes();
	ObservableEntityType<?> getEntityType(String entityName);
	<E> ObservableEntityType<E> getEntityType(Class<E> type);

	default <E, X extends E> ObservableEntity<? extends E> observableEntity(E entity) throws IllegalArgumentException {
		ObservableEntityType<X> type = getEntityType((Class<X>) entity.getClass());
		if (type == null)
			throw new IllegalArgumentException("Entity type " + entity.getClass().getName() + " not supported");
		return type.observableEntity((X) entity);
	}
}
