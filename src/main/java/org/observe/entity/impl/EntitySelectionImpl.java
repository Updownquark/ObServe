package org.observe.entity.impl;

import java.util.function.Function;

import org.observe.entity.EntityCondition;
import org.observe.entity.EntityDeletion;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.EntityQuery;
import org.observe.entity.EntitySelection;
import org.observe.entity.EntityUpdate;
import org.observe.entity.ObservableEntityType;
import org.qommons.collect.ParameterSet.ParameterMap;

public class EntitySelectionImpl<E> extends AbstractEntityOperation<E> implements EntitySelection<E> {
	private final EntityConditionImpl<E> theCondition;

	public EntitySelectionImpl(ObservableEntityType<E> type, ParameterMap<EntityOperationVariable<E, ?>> variables,
		EntityConditionImpl<E> condition) {
		super(type, variables, null);
		theCondition = condition;
	}

	@Override
	public EntitySelection<E> where(Function<EntityCondition<E>, EntityCondition<E>> condition) {
		EntityConditionImpl<E> newCondition = (EntityConditionImpl<E>) condition.apply(theCondition);
		if (newCondition != condition)
			return new EntitySelectionImpl<>(getEntityType(), extractVariables(newCondition), newCondition);
		return this;
	}

	private ParameterMap<EntityOperationVariable<E, ?>> extractVariables(EntityConditionImpl<E> newCondition) {
		// TODO Auto-generated method stub
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
