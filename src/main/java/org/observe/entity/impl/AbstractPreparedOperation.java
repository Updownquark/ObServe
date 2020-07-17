package org.observe.entity.impl;

import org.observe.entity.ConfigurableOperation;
import org.observe.entity.PreparedOperation;
import org.qommons.collect.QuickSet.QuickMap;

abstract class AbstractPreparedOperation<E, O extends PreparedOperation<E>> implements PreparedOperation<E> {
	private final ConfigurableOperation<E> theDefinition;
	private final Object thePreparedObject;
	private final QuickMap<String, Object> theVariableValues;

	AbstractPreparedOperation(ConfigurableOperation<E> definition, Object preparedObject, QuickMap<String, Object> variableValues) {
		theDefinition = definition;
		thePreparedObject = preparedObject;
		theVariableValues = variableValues;
	}

	protected Object getPreparedObject() {
		return thePreparedObject;
	}

	@Override
	public ConfigurableOperation<E> getDefinition() {
		return theDefinition;
	}

	@Override
	public QuickMap<String, Object> getVariableValues() {
		return theVariableValues;
	}

	@Override
	public O satisfy(String variableName, Object value) throws IllegalArgumentException {
		int idx = theDefinition.getVariables().keySet().indexOf(variableName);
		if (idx < 0)
			throw new IllegalArgumentException("No such variable " + variableName);
		QuickMap<String, Object> vvCopy = theVariableValues.keySet().createMap();
		for (int i = 0; i < theVariableValues.keySet().size(); i++) {
			if (i != idx)
				vvCopy.put(i, theVariableValues.get(i));
		}
		vvCopy.put(idx, value);
		return copy(vvCopy.unmodifiable());
	}

	protected abstract O copy(QuickMap<String, Object> variableValues);

	@Override
	protected void finalize() throws Throwable {
		((ObservableEntityDataSetImpl) theDefinition.getEntityType().getEntitySet()).getImplementation().dispose(thePreparedObject);
		super.finalize();
	}
}
