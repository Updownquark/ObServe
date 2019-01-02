package org.observe.entity;

import org.qommons.collect.ParameterSet.ParameterMap;

public class EntityIdentity<E> {
	private final ObservableEntityType<E> theEntityType;
	final ParameterMap<IdentityField<E, ?>> theFields;

	private EntityIdentity(ObservableEntityType<E> entityType, Object[] fieldValues) {
		theEntityType = entityType;
		ParameterMap<IdentityField<E, ?>> fieldMap = entityType.getIdentityFields().keySet().createMap();
		for (int i = 0; i < fieldValues.length; i++)
			fieldMap.put(i, new IdentityField<>((IdentityFieldType<E, Object>) entityType.getIdentityFields().get(i), fieldValues[i]));
		theFields = fieldMap.unmodifiable();
	}

	public ObservableEntityType<E> getEntityType() {
		return theEntityType;
	}

	public ParameterMap<IdentityField<E, ?>> getFields() {
		return theFields;
	}
}
