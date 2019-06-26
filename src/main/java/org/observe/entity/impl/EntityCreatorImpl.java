package org.observe.entity.impl;

import org.observe.entity.EntityCreator;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.EntityValueAccess;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.PreparedCreator;
import org.qommons.collect.ParameterSet.ParameterMap;

public class EntityCreatorImpl<E> extends AbstractEntityOperation<E> implements EntityCreator<E> {
	private final ParameterMap<Object> theIdFieldValues;
	private final ParameterMap<Object> theFieldValues;

	public EntityCreatorImpl(ObservableEntityType<E> entityType, ParameterMap<EntityOperationVariable<E, ?>> variables,
		ParameterMap<Object> idFieldValues, ParameterMap<Object> fieldValues) {
		super(entityType, variables);
		theIdFieldValues = idFieldValues;
		theFieldValues = fieldValues;
	}

	@Override
	protected EntityCreatorImpl<E> copy(ParameterMap<EntityOperationVariable<E, ?>> variables) {
		return new EntityCreatorImpl<>(getEntityType(), variables, theIdFieldValues, theFieldValues);
	}

	@Override
	protected EntityCreatorImpl<E> addVariable(String variable, EntityValueAccess<E, ?> value) {
		return (EntityCreatorImpl<E>) super.addVariable(variable, value);
	}

	@Override
	public <F> EntityCreator<E> with(ObservableEntityFieldType<? super E, F> field, F value) {
		int idx=getEntityType().getIdentityFields().keySet().indexOf(field.getName());
		if (idx >= 0) {
			if (getEntityType().getIdentityFields().get(idx) != field)
				throw new IllegalArgumentException("No such field: " + getEntityType() + "." + field);
			ParameterMap<Object> copy = theIdFieldValues.copy();
			copy.put(idx, value);
			return new EntityCreatorImpl<>(getEntityType(), getVariables(), copy, theFieldValues);
		}
		idx = getEntityType().getFields().keySet().indexOf(field.getName());
		if (idx >= 0) {
			if (getEntityType().getFields().get(idx) != field)
				throw new IllegalArgumentException("No such field: " + getEntityType() + "." + field);
			ParameterMap<Object> copy = theFieldValues.copy();
			copy.put(idx, value);
			return new EntityCreatorImpl<>(getEntityType(), getVariables(), theIdFieldValues, copy);
		}
		throw new IllegalArgumentException("No such field: " + getEntityType() + "." + field);
	}

	@Override
	public EntityCreator<E> withVariable(ObservableEntityFieldType<? super E, ?> field, String variableName) {
		return addVariable(variableName, getEntityType().fieldValue(field));
	}

	@Override
	public PreparedCreator<E> prepare() throws IllegalStateException, EntityOperationException {
	}

	@Override
	public EntityIdentity<E> create() throws IllegalStateException, EntityOperationException {
		// TODO Auto-generated method stub
	}

	@Override
	public ObservableEntity<E> createAndGet() throws IllegalStateException, EntityOperationException {
		// TODO Auto-generated method stub
	}
}
