package org.observe.entity.impl;

import org.observe.entity.ConfigurableUpdate;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntitySelection;
import org.observe.entity.PreparedUpdate;
import org.qommons.collect.QuickSet.QuickMap;

public class PreparedUpdateImpl<E> extends AbstractPreparedSetOperation<E, PreparedUpdateImpl<E>> implements PreparedUpdate<E> {
	PreparedUpdateImpl(ConfigurableUpdate<E> definition, Object preparedObject, QuickMap<String, Object> variableValues,
		EntitySelection<E> selection) {
		super(definition, preparedObject, variableValues, selection);
	}

	@Override
	public ConfigurableUpdate<E> getDefinition() {
		return (ConfigurableUpdate<E>) super.getDefinition();
	}

	@Override
	public long execute() throws IllegalStateException, EntityOperationException {
		return ((ObservableEntityTypeImpl<E>) getEntityType()).update(this, getPreparedObject());
	}
}