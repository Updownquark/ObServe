package org.observe.entity.impl;

import org.observe.entity.EntityOperation;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.PreparedOperation;
import org.observe.util.TypeTokens;
import org.qommons.collect.ParameterSet.ParameterMap;

public abstract class AbstractPreparedOperation<E, O extends PreparedOperation<E>> implements PreparedOperation<E> {
	private final EntityOperation<E> theDefinition;
	private final ParameterMap<Object> theVariableValues;

	public AbstractPreparedOperation(EntityOperation<E> definition, ParameterMap<Object> variableValues) {
		theDefinition = definition;
		theVariableValues = variableValues;
	}

	@Override
	public EntityOperation<E> getDefinition() {
		return theDefinition;
	}

	@Override
	public ParameterMap<Object> getVariableValues() {
		return theVariableValues;
	}

	@Override
	public O satisfy(String variableName, Object value) throws IllegalArgumentException {
		int idx = theDefinition.getVariables().keySet().indexOf(variableName);
		if (idx < 0)
			throw new IllegalArgumentException("No such variable " + variableName);
		EntityOperationVariable<E, ?> vbl = theDefinition.getVariables().get(idx);
		if (value != null && !TypeTokens.get().isInstance(vbl.getValue().getType(), value))
			throw new IllegalArgumentException(
				value.getClass().getName() + " cannot be assigned to variable " + variableName + ", type " + vbl.getValue().getType());
		String msg = ((EntityOperationVariable<E, Object>) vbl).getValue().canAccept(value);
		if (msg != null)
			throw new IllegalArgumentException(variableName + ": " + msg);
		ParameterMap<Object> vvCopy = theVariableValues.keySet().createMap();
		for (int i = 0; i < theVariableValues.keySet().size(); i++) {
			if (i != idx)
				vvCopy.put(i, theVariableValues.get(i));
		}
		vvCopy.put(idx, value);
		return copy(vvCopy.unmodifiable());
	}

	protected abstract O copy(ParameterMap<Object> variableValues);
}
