package org.observe.entity.impl;

import org.observe.entity.EntityCreator;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.qommons.collect.ParameterSet.ParameterMap;

public class EntityCreatorImpl<E> extends AbstractEntityOperation<E> implements EntityCreator<E> {
	private final ParameterMap<Object> theFieldValues;

	public EntityCreatorImpl(ObservableEntityType<E> entityType, ParameterMap<EntityOperationVariable<E, ?>> variables,
		Object[] variableValues, ParameterMap<Object> fieldValues) {
		super(entityType, variables, variableValues);
		theFieldValues = fieldValues;
	}

	@Override
	public <F> EntityCreator<E> with(ObservableEntityFieldType<? super E, F> field, F value) {
		return copy(addVariable(getVariables(), new EntityOperationVariable<>(this, null, null)
			// TODO Auto-generated method stub
			return null;
	}

	@Override
	public EntityCreator<E> withVariable(ObservableEntityFieldType<? super E, ?> field, String variableName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public EntityCreator<E> prepare() throws IllegalStateException, EntityOperationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public EntityCreator<E> satisfy(String variableName, Object value) throws IllegalStateException, IllegalArgumentException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ObservableEntity<E> create() throws IllegalStateException, EntityOperationException {
		// TODO Auto-generated method stub
		return null;
	}

}
