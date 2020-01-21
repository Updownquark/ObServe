package org.observe.entity.impl;

import java.util.function.Function;

import org.observe.entity.EntityCondition;
import org.observe.entity.EntityDeletion;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.EntityQuery;
import org.observe.entity.EntitySelection;
import org.observe.entity.EntityUpdate;
import org.qommons.collect.QuickSet.QuickMap;

public class EntitySelectionImpl<E> implements EntitySelection<E> {
	private final EntityCondition<E> theCondition;
	private final QuickMap<String, EntityOperationVariable<E>> theVariables;

	public EntitySelectionImpl(EntityCondition<E> condition) {
		theCondition = condition;
		theVariables = QuickMap.of(theCondition.getVariables(), String::compareTo);
	}

	@Override
	public EntityCondition<E> getCondition() {
		return theCondition;
	}

	@Override
	public EntitySelection<E> where(Function<EntityCondition<E>, EntityCondition<E>> condition) {
		EntityCondition<E> newCondition = condition.apply(theCondition);
		if (newCondition != condition)
			return new EntitySelectionImpl<>(newCondition);
		return this;
	}

	@Override
	public EntityQuery<E> query() {
		return new ConfigurableQueryImpl<>(this);
	}

	@Override
	public EntityUpdate<E> update() {
		return new ConfigurableUpdateImpl<>(this, theVariables,
			getEntityType().getFields().keySet().<Object> createMap().fill(EntityUpdate.NOT_SET).unmodifiable(),
			getEntityType().getFields().keySet().<EntityOperationVariable<E>> createMap().unmodifiable());
	}

	@Override
	public EntityDeletion<E> delete() {
		return new ConfigurableDeletionImpl<>(this, theVariables);
	}
}
