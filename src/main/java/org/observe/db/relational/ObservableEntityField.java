package org.observe.db.relational;

import org.observe.SettableValue;

public interface ObservableEntityField<E extends ObservableEntity<E>, T> extends SettableValue<T> {
	ObservableEntityFieldType<E, T> getFieldType();
}
