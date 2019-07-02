package org.observe.entity.impl;

import org.observe.entity.EntityCreator;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityOperationException;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.PreparedCreator;
import org.qommons.collect.ParameterSet.ParameterMap;

public class PreparedCreatorImpl<E> extends AbstractPreparedOperation<E, PreparedCreatorImpl<E>> implements PreparedCreator<E> {
	private final ParameterMap
	PreparedCreatorImpl(EntityCreatorImpl<E> definition, Object preparedObject, ParameterMap<Object> variableValues) {
		super(definition, preparedObject, variableValues);
		// TODO Auto-generated constructor stub
	}

	@Override
	public <F> EntityCreator<E> with(ObservableEntityFieldType<? super E, F> field, F value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public EntityCreator<E> withVariable(ObservableEntityFieldType<? super E, ?> field, String variableName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public EntityIdentity<E> create() throws IllegalStateException, EntityOperationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ObservableEntity<E> createAndGet() throws IllegalStateException, EntityOperationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected PreparedCreatorImpl<E> copy(ParameterMap<Object> variableValues) {
		// TODO Auto-generated method stub
		return null;
	}

}
