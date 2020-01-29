package org.observe.entity.impl;

import org.observe.entity.ConfigurableDeletion;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityModificationResult;
import org.observe.entity.EntityOperationException;
import org.observe.entity.PreparedDeletion;
import org.qommons.collect.QuickSet.QuickMap;

class PreparedDeletionImpl<E> extends AbstractPreparedSetOperation<E, PreparedDeletionImpl<E>> implements PreparedDeletion<E> {
	PreparedDeletionImpl(ConfigurableDeletion<E> definition, Object preparedObject, QuickMap<String, Object> variableValues,
		EntityCondition<E> selection) {
		super(definition, preparedObject, variableValues, selection);
	}

	@Override
	public ConfigurableDeletion<E> getDefinition() {
		return (ConfigurableDeletion<E>) super.getDefinition();
	}

	@Override
	public EntityModificationResult<E> execute(boolean sync, Object cause) throws IllegalStateException, EntityOperationException {
		return ((ObservableEntityTypeImpl<E>) getEntityType()).getEntitySet().delete(this, sync, cause);
	}
}
