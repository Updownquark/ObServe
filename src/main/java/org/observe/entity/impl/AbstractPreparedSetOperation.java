package org.observe.entity.impl;

import org.observe.entity.ConfigurableOperation;
import org.observe.entity.EntitySelection;
import org.observe.entity.EntitySetOperation;
import org.observe.entity.ObservableEntityType;
import org.qommons.collect.QuickSet.QuickMap;

public abstract class AbstractPreparedSetOperation<E, O extends AbstractPreparedSetOperation<E, O>> extends AbstractPreparedOperation<E, O>
implements EntitySetOperation<E> {
	private final EntitySelection<E> theSelection;

	public AbstractPreparedSetOperation(ConfigurableOperation<E> definition, Object preparedObject,
		QuickMap<String, Object> variableValues, EntitySelection<E> selection) {
		super(definition, preparedObject, variableValues);
		theSelection = selection;
	}

	@Override
	public ObservableEntityType<E> getEntityType() {
		return super.getEntityType();
	}

	@Override
	public EntitySelection<E> getSelection() {
		return theSelection;
	}

	@Override
	protected O copy(QuickMap<String, Object> variableValues) {
		// TODO Auto-generated method stub
		return null;
	}
}
