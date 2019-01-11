package org.observe.entity;

import com.google.common.reflect.TypeToken;

public class IdentityFieldType<E, T> implements ObservableEntityFieldType<E, T> {
	private final ObservableEntityType<E> theEntityType;
	private final String theName;
	private final TypeToken<T> theFieldType;
	final int theIndex;

	public IdentityFieldType(ObservableEntityType<E> entityType, String name, TypeToken<T> fieldType, int index) {
		theEntityType = entityType;
		theName = name;
		theFieldType = fieldType;
		theIndex = index;
	}

	@Override
	public ObservableEntityType<E> getEntityType() {
		return theEntityType;
	}

	@Override
	public TypeToken<T> getFieldType() {
		return theFieldType;
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public IdentityField<E, T> getField(ObservableEntity<? extends E> entity) {
		return (IdentityField<E, T>) entity.getId().theFields.get(theIndex);
	}
}
