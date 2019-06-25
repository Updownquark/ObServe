package org.observe.entity.impl;

import java.util.function.Function;

import org.observe.entity.EntityCondition;
import org.observe.entity.EntityDeletion;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.EntityQuery;
import org.observe.entity.EntitySelection;
import org.observe.entity.EntityUpdate;
import org.observe.entity.ObservableEntityType;
import org.qommons.collect.ParameterSet;
import org.qommons.collect.ParameterSet.ParameterMap;

public class EntitySelectionImpl<E> extends AbstractEntityOperation<E> implements EntitySelection<E> {
	private final EntityCondition<E> theCondition;

	public EntitySelectionImpl(ObservableEntityType<E> type, ParameterMap<EntityOperationVariable<E, ?>> variables) {
		this(type, variables, null);
	}

	public EntitySelectionImpl(ObservableEntityType<E> type, ParameterMap<EntityOperationVariable<E, ?>> variables,
		EntityCondition<E> condition) {
		super(type, variables, null);
		theCondition = condition == null ? new EntityCondition.None<>(this) : condition;
	}

	@Override
	protected AbstractEntityOperation<E> copy(ParameterMap<EntityOperationVariable<E, ?>> variables, ParameterMap<Object> variableValues) {
		throw new IllegalStateException("This is a precursor operation");
	}

	@Override
	public EntitySelection<E> where(Function<EntityCondition<E>, EntityCondition<E>> condition) {
		EntityCondition<E> newCondition = condition.apply(theCondition);
		if (newCondition != condition)
			return new EntitySelectionImpl<>(getEntityType(), extractVariables(newCondition), newCondition);
		return this;
	}

	private ParameterMap<EntityOperationVariable<E, ?>> extractVariables(EntityCondition<E> newCondition) {
		ParameterMap<EntityOperationVariable<E, ?>> vars = ParameterSet.of(newCondition.getVariables().keySet()).createMap();
		for (int i = 0; i < vars.keySet().size(); i++)
			vars.put(i, newCondition.getVariables().get(vars.keySet().get(i)));
		return vars.unmodifiable();
	}

	@Override
	public EntityQuery<E> query() {
		// TODO Auto-generated method stub
	}

	@Override
	public EntityUpdate<E> update() {
		// TODO Auto-generated method stub
	}

	@Override
	public EntityDeletion<E> delete() {
		// TODO Auto-generated method stub
	}
}
