package org.observe.entity.impl;

import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityOperationException;
import org.observe.entity.ObservableEntity;
import org.observe.entity.PreparedCreator;
import org.qommons.collect.QuickSet.QuickMap;

public class PreparedCreatorImpl<E> extends AbstractPreparedOperation<E, PreparedCreatorImpl<E>> implements PreparedCreator<E> {
	PreparedCreatorImpl(ConfigurableCreatorImpl<E> definition, Object preparedObject, QuickMap<String, Object> variableValues) {
		super(definition, preparedObject, variableValues);
	}

	@Override
	public ConfigurableCreatorImpl<E> getDefinition() {
		return (ConfigurableCreatorImpl<E>) super.getDefinition();
	}

	@Override
	public EntityIdentity<E> create() throws IllegalStateException, EntityOperationException {
		return ((ObservableEntityTypeImpl<E>) getEntityType()).create(this, getPreparedObject());
	}

	@Override
	public ObservableEntity<E> createAndGet() throws IllegalStateException, EntityOperationException {
		return ((ObservableEntityTypeImpl<E>) getEntityType()).createAndGet(this, getPreparedObject());
	}

	@Override
	protected PreparedCreatorImpl<E> copy(QuickMap<String, Object> variableValues) {
		return new PreparedCreatorImpl<>(getDefinition(), getPreparedObject(), variableValues);
	}
}
