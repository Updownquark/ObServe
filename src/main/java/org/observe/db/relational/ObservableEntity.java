package org.observe.db.relational;

import java.util.NavigableSet;

public interface ObservableEntity<E extends ObservableEntity<E>> {
	EntitySource getSource();
	long getId();
	NavigableSet<ObservableEntityField<? super E, ?>> getFields();
}
