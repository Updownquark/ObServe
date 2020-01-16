package org.observe.entity;

import java.util.List;

public interface IdentityFieldType<E, T> extends ObservableEntityFieldType<E, T> {
	int getIdIndex();

	@Override
	List<? extends IdentityFieldType<? super E, T>> getOverrides();
}
