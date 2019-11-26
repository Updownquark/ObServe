package org.observe.entity;

import java.util.function.Function;

import org.qommons.Transactable;
import org.qommons.collect.QuickSet.QuickMap;

public interface ObservableEntityType<E> extends Transactable {
	ObservableEntityType<? super E> getParent();
	ObservableEntityType<? super E> getRoot();
	String getEntityName();
	Class<E> getEntityType();

	QuickMap<String, ObservableEntityFieldType<? super E, ?>> getFields();
	QuickMap<String, IdentityFieldType<? super E, ?>> getIdentityFields();

	ObservableEntity<? extends E> observableEntity(EntityIdentity<? super E> id);
	ObservableEntity<? extends E> observableEntity(E entity);

	EntitySelection<E> select();
	EntityCreator<E> create();

	<F> EntityValueAccess<E, F> fieldValue(ObservableEntityFieldType<? super E, F> field);
	<F> ObservableEntityFieldType<? super E, F> getField(Function<? super E, F> fieldGetter);
	default <F> EntityValueAccess<E, F> fieldAccess(Function<? super E, F> fieldGetter) {
		return fieldValue(getField(fieldGetter));
	}
}
