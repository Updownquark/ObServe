package org.observe.entity;

import org.qommons.collect.ParameterSet.ParameterMap;

public class GenericPersistedEntity<E> {
	public final EntityIdentity<E> id;
	public final ParameterMap<Object> fields;

	public GenericPersistedEntity(EntityIdentity<E> id, ParameterMap<Object> fields) {
		super();
		this.id = id;
		this.fields = fields;
	}
}