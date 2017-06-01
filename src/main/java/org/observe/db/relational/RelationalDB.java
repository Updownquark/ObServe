package org.observe.db.relational;

import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedSet;

public interface RelationalDB {
	ObservableCollection<EntitySource> getSources();
	ObservableSortedSet<ObservableEntityType<?>> getEntityTypes();
	<E extends ObservableEntity<E>> ObservableEntityType<E> getEntityType(Class<E> type);

	<E extends ObservableEntity<E>> RelationalQueryBuilder<E> query(ObservableEntityType<E> type);
	<E extends ObservableEntity<E>> RelationalQueryBuilder<E> query(Class<E> type);
}
