package org.observe.entity;

import java.util.List;

import org.observe.ObservableValue;
import org.observe.collect.ObservableSortedSet;
import org.qommons.collect.QuickSet.QuickMap;

public interface EntityQuery<E> extends EntitySetOperation<E> {
	QuickMap<String, FieldLoadType> getFieldLoadTypes();
	List<QueryOrder<E, ?>> getOrder();

	EntityQuery<E> loadField(ObservableEntityFieldType<E, ?> field, FieldLoadType type);
	EntityQuery<E> loadAllFields(FieldLoadType type);

	ObservableValue<Long> count() throws IllegalStateException, EntityOperationException;
	ObservableSortedSet<E> collect() throws IllegalStateException, EntityOperationException;
	ObservableSortedSet<ObservableEntity<? extends E>> collectObservable(boolean withUpdates)
		throws IllegalStateException, EntityOperationException;
	ObservableSortedSet<EntityIdentity<? super E>> collectIdentities() throws IllegalStateException, EntityOperationException;
}
