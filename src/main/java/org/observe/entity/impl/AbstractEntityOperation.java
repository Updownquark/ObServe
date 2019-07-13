package org.observe.entity.impl;

import java.util.ArrayList;
import java.util.List;

import org.observe.entity.EntityOperation;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.EntityValueAccess;
import org.observe.entity.ObservableEntityType;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;

public abstract class AbstractEntityOperation<E> implements EntityOperation<E> {
	private final ObservableEntityType<E> theType;
	private final QuickMap<String, EntityOperationVariable<E, ?>> theVariables;

	public AbstractEntityOperation(ObservableEntityType<E> type, QuickMap<String, EntityOperationVariable<E, ?>> variables) {
		theType = type;
		theVariables = variables;
	}

	protected abstract AbstractEntityOperation<E> copy(QuickMap<String, EntityOperationVariable<E, ?>> variables);

	@Override
	public ObservableEntityType<E> getEntityType() {
		return theType;
	}

	@Override
	public QuickMap<String, EntityOperationVariable<E, ?>> getVariables() {
		return theVariables;
	}

	protected AbstractEntityOperation<E> addVariable(String variable, EntityValueAccess<E, ?> value) {
		if (theVariables.keySet().contains(variable))
			throw new IllegalArgumentException("A variable named " + variable + " is already present in this operation");
		List<String> vars = new ArrayList<>(theVariables.keySet().size() + 1);
		vars.addAll(theVariables.keySet());
		vars.add(variable);
		QuickMap<String, EntityOperationVariable<E, ?>> newVars = QuickSet.of(vars).createMap();
		for (int i = 0; i < theVariables.keySet().size(); i++)
			newVars.put(theVariables.keySet().get(i), theVariables.get(i));
		newVars.put(variable, new EntityOperationVariable<>(this, variable, value));
		return copy(newVars.unmodifiable());
	}
}
