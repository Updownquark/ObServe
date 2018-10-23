package org.observe.db.relational;

import com.google.common.reflect.TypeToken;

public interface ObservableEntityFieldType<E extends ObservableEntity<? extends E>, T> extends Comparable<ObservableEntityFieldType<?, ?>> {
	ObservableEntityType<E> getEntityType();
	String getName();
	TypeToken<T> getFieldType();

	@Override
	default int compareTo(ObservableEntityFieldType<?, ?> other) {
		int comp = getName().compareToIgnoreCase(other.getName());
		if (comp == 0)
			comp = getName().compareTo(other.getName());
		return comp;
	}
}
