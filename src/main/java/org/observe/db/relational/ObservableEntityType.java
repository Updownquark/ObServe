package org.observe.db.relational;

import java.util.NavigableSet;
import java.util.function.Function;

import com.google.common.reflect.TypeToken;

public interface ObservableEntityType<E extends ObservableEntity<E>> {
	TypeToken<E> getType();
	NavigableSet<ObservableEntityFieldType<? super E, ?>> getFields();
	<T> ObservableEntityFieldType<? super E, T> getField(Function<? super E, T> field);
}
