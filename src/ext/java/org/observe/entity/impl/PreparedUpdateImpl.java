package org.observe.entity.impl;

import org.observe.entity.EntityCondition;
import org.observe.entity.EntityModificationResult;
import org.observe.entity.EntityOperationException;
import org.observe.entity.PreparedUpdate;
import org.qommons.collect.QuickSet.QuickMap;

class PreparedUpdateImpl<E> extends AbstractPreparedSetOperation<E, PreparedUpdateImpl<E>> implements PreparedUpdate<E> {
	PreparedUpdateImpl(ConfigurableUpdateImpl<E> definition, Object preparedObject, QuickMap<String, Object> variableValues,
		EntityCondition<E> selection) {
		super(definition, preparedObject, variableValues, selection);
	}

	@Override
	public ConfigurableUpdateImpl<E> getDefinition() {
		return (ConfigurableUpdateImpl<E>) super.getDefinition();
	}

	@Override
	public EntityModificationResult<E> execute(boolean sync, Object cause) throws IllegalStateException, EntityOperationException {
		return ((ObservableEntityTypeImpl<E>) getEntityType()).getEntitySet().update(this, sync, cause);
	}
}
