package org.observe.entity.impl;

import org.observe.entity.EntityCreationResult;
import org.observe.entity.EntityOperationException;
import org.observe.entity.PreparedCreator;
import org.qommons.collect.QuickSet.QuickMap;

class PreparedCreatorImpl<E> extends AbstractPreparedOperation<E, PreparedCreatorImpl<E>> implements PreparedCreator<E> {
	PreparedCreatorImpl(ConfigurableCreatorImpl<E> definition, Object preparedObject, QuickMap<String, Object> variableValues) {
		super(definition, preparedObject, variableValues);
	}

	@Override
	public ConfigurableCreatorImpl<E> getDefinition() {
		return (ConfigurableCreatorImpl<E>) super.getDefinition();
	}

	@Override
	public EntityCreationResult<E> create(boolean sync, Object cause) throws IllegalStateException, EntityOperationException {
		return ((ObservableEntityTypeImpl<E>) getEntityType()).getEntitySet().create(this, sync, cause);
	}

	@Override
	protected PreparedCreatorImpl<E> copy(QuickMap<String, Object> variableValues) {
		return new PreparedCreatorImpl<>(getDefinition(), getPreparedObject(), variableValues);
	}
}
