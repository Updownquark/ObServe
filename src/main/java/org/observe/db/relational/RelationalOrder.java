package org.observe.db.relational;

public interface RelationalOrder<E extends ObservableEntity<? extends E>> {
	ObservableEntityType<E> getType();

	RelationalOrder<E> sortOn(ObservableEntityFieldType<E, ?> field, boolean ascending);
}
