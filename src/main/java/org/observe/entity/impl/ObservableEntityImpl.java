package org.observe.entity.impl;

import org.observe.entity.EntityIdentity;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityField;
import org.observe.entity.ObservableEntityFieldType;
import org.qommons.collect.QuickSet.QuickMap;

class ObservableEntityImpl<E> implements ObservableEntity<E> {
	private final ObservableEntityTypeImpl<E> theType;
	private final EntityIdentity<? super E> theId;
	private final E theEntity;
	private final QuickMap<String, ObservableEntityField<? super E, ?>> theFields;

	ObservableEntityImpl(ObservableEntityTypeImpl<E> type, EntityIdentity<? super E> id,
		QuickMap<String, ObservableEntityField<? super E, ?>> fields, E entity) {
		theType = type;
		theId = id;
		theFields = fields;
		theEntity = entity;
	}

	@Override
	public ObservableEntityTypeImpl<E> getType() {
		return theType;
	}

	@Override
	public EntityIdentity<? super E> getId() {
		return theId;
	}

	@Override
	public <F> ObservableEntityField<? super E, F> getField(ObservableEntityFieldType<? super E, F> fieldType) {
		ObservableEntityField<? super E, ?> field = theFields.getIfPresent(fieldType.getName());
		if (field == null)
			throw new IllegalArgumentException("Field type " + fieldType + " does not apply to entity of type " + theType);
		return (ObservableEntityField<? super E, F>) field;
	}

	@Override
	public E getEntity() {
		return theEntity;
	}

	@Override
	public int hashCode() {
		return theId.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof ObservableEntity && theId.equals(((ObservableEntity<?>) obj).getId());
	}

	@Override
	public String toString() {
		return theId.toString();
	}
}
