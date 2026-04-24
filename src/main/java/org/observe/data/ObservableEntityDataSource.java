package org.observe.data;

import org.observe.config.SyncValueSet;
import org.qommons.Stamped;
import org.qommons.Transactable;

public interface ObservableEntityDataSource extends Transactable, Stamped {
	<E> ReflectedEntityValueType<E> getType(Class<E> type);

	<E> SyncValueSet<E> observeEntities(Class<E> type) throws IllegalArgumentException;
}
