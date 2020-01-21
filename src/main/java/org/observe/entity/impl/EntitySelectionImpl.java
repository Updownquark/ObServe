package org.observe.entity.impl;

import java.util.function.Function;

import org.observe.entity.EntityCondition;
import org.observe.entity.EntityDeletion;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.EntityQuery;
import org.observe.entity.EntitySelection;
import org.observe.entity.EntityUpdate;
import org.observe.entity.ObservableEntityType;
import org.qommons.collect.QuickSet;
import org.qommons.collect.QuickSet.QuickMap;

public class EntitySelectionImpl<E> extends AbstractEntityOperation<E> implements EntitySelection<E> {
	private final EntityCondition<E> theCondition;

	public EntitySelectionImpl(ObservableEntityType<E> type, QuickMap<String, EntityOperationVariable<? super E, ?>> variables) {
		this(type, variables, null);
	}

	public EntitySelectionImpl(ObservableEntityType<E> type, QuickMap<String, EntityOperationVariable<? super E, ?>> variables,
		EntityCondition<E> condition) {
		super(type, variables);
		theCondition = condition == null ? new EntityCondition.None<>(type) : condition;
	}

	@Override
	protected AbstractEntityOperation<E> copy(QuickMap<String, EntityOperationVariable<? super E, ?>> variables) {
		throw new IllegalStateException("This is a precursor operation");
	}

	@Override
	public EntitySelection<E> where(Function<EntityCondition<E>, EntityCondition<E>> condition) {
		EntityCondition<E> newCondition = condition.apply(theCondition);
		if (newCondition != condition)
			return new EntitySelectionImpl<>(getEntityType(), extractVariables(newCondition), newCondition);
		return this;
	}

	private QuickMap<String, EntityOperationVariable<? super E, ?>> extractVariables(EntityCondition<? super E> newCondition) {
		QuickMap<String, EntityOperationVariable<? super E, ?>> vars = QuickSet.of(newCondition.getVariables().keySet()).createMap();
		for (int i = 0; i < vars.keySet().size(); i++)
			vars.put(i, newCondition.getVariables().get(vars.keySet().get(i)));
		return vars.unmodifiable();
	}

	@Override
	public EntityQuery<E> query() {
		return new EntityQueryImpl<>(this);
	}

	@Override
	public EntityUpdate<E> update() {
		return new EntityUpdateImpl<>(this);
	}

	@Override
	public EntityDeletion<E> delete() {
		return new EntityDeleteImpl<>(this);
	}
}
