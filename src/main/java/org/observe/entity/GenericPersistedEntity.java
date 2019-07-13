package org.observe.entity;

import org.qommons.collect.QuickSet.QuickMap;

public class GenericPersistedEntity<E> {
	public final EntityIdentity<E> id;
	public final QuickMap<String, Object> fields;

	public GenericPersistedEntity(EntityIdentity<E> id, QuickMap<String, Object> fields) {
		super();
		this.id = id;
		this.fields = fields;
	}
}
