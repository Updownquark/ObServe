package org.observe.entity.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.observe.entity.EntityOperation;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.EntityValueAccess;
import org.observe.entity.ObservableEntityType;
import org.observe.util.TypeTokens;
import org.qommons.collect.ParameterSet;
import org.qommons.collect.ParameterSet.ParameterMap;

public abstract class AbstractEntityOperation<E> implements EntityOperation<E> {
	private final ObservableEntityType<E> theType;
	private final boolean isPrepared;
	private final ParameterMap<EntityOperationVariable<E, ?>> theVariables;
	private final Object[] theVariableValues;

	public AbstractEntityOperation(ObservableEntityType<E> type, ParameterMap<EntityOperationVariable<E, ?>> variables,
		Object[] variableValues) {
		theType = type;
		isPrepared = variableValues != null;
		theVariables = variables;
		theVariableValues = variableValues;
	}

	protected abstract AbstractEntityOperation<E> copy(ParameterMap<EntityOperationVariable<E, ?>> variables, Object[] variableValues);

	@Override
	public ObservableEntityType<E> getEntityType() {
		return theType;
	}

	@Override
	public boolean isPrepared() {
		return isPrepared;
	}

	@Override
	public ParameterMap<EntityOperationVariable<E, ?>> getVariables() {
		return theVariables;
	}

	protected AbstractEntityOperation<E> addVariable(String variable, EntityValueAccess<E, ?> value) {
		if (theVariableValues != null)
			throw new IllegalStateException("Already prepared");
		if (theVariables.keySet().contains(variable))
			throw new IllegalArgumentException("A variable named " + variable + " is already present in this operation");
		List<String> vars = new ArrayList<>(theVariables.keySet().size() + 1);
		vars.addAll(theVariables.keySet());
		vars.add(variable);
		ParameterMap<EntityOperationVariable<E, ?>> newVars = ParameterSet.of(vars).createMap();
		for (int i = 0; i < theVariables.keySet().size(); i++)
			newVars.put(theVariables.keySet().get(i), theVariables.get(i));
		newVars.put(variable, new EntityOperationVariable<>(this, variable, value));
		return copy(newVars.unmodifiable(), null);
	}

	@Override
	public EntityOperation<E> satisfy(String variableName, Object value) throws IllegalStateException, IllegalArgumentException {
		if (!isPrepared)
			throw new IllegalStateException("Not a prepared operation");
		int idx = theVariables.keySet().indexOf(variableName);
		if (idx < 0)
			throw new IllegalArgumentException("No such variable " + variableName);
		EntityOperationVariable<E, ?> vbl = theVariables.get(idx);
		if (value != null && !TypeTokens.get().isInstance(vbl.getValue().getType(), value))
			throw new IllegalArgumentException(
				value.getClass().getName() + " cannot be assigned to variable " + variableName + ", type " + vbl.getValue().getType());
		String msg = ((EntityOperationVariable<E, Object>) vbl).getValue().canAccept(value);
		if (msg != null)
			throw new IllegalArgumentException(variableName + ": " + msg);
		Object[] vvCopy = Arrays.copyOf(theVariableValues, theVariableValues.length);
		vvCopy[idx] = value;
		return copy(theVariables, vvCopy);
	}

}
