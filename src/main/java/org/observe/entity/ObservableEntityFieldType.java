package org.observe.entity;

import java.util.List;

import com.google.common.reflect.TypeToken;

public interface ObservableEntityFieldType<E, F> extends EntityValueAccess<E, F> {
	ObservableEntityType<E> getEntityType();
	TypeToken<F> getFieldType();
	String getName();
	int getFieldIndex();

	List<? extends ObservableEntityFieldType<? super E, F>> getOverrides();
	List<FieldConstraint<E, F>> getConstraints();

	ObservableEntityField<E, F> getValue(ObservableEntity<? extends E> entity);

	@Override
	default TypeToken<F> getValueType() {
		return getFieldType();
	}

	@Override
	default F getValue(E entity) {
		return getValue(getEntityType().observableEntity(entity)).get();
	}
}
