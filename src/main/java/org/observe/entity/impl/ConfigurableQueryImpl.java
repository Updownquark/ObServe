package org.observe.entity.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.ObservableValue;
import org.observe.collect.ObservableSortedSet;
import org.observe.entity.ConfigurableQuery;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityValueAccess;
import org.observe.entity.FieldLoadType;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.PreparedQuery;
import org.observe.entity.QueryOrder;
import org.qommons.collect.QuickSet.QuickMap;

class ConfigurableQueryImpl<E> extends AbstractConfigurableOperation<E> implements ConfigurableQuery<E> {
	private final EntityCondition<E> theSelection;
	private final QuickMap<String, FieldLoadType> theFieldLoadTypes;
	private final List<QueryOrder<E, ?>> theOrder;

	ConfigurableQueryImpl(EntityCondition<E> selection) {
		this(selection, QuickMap.of(selection.getVariables(), String::compareTo),
			selection.getEntityType().getFields().keySet().createMap(), Collections.emptyList());
	}

	private ConfigurableQueryImpl(EntityCondition<E> selection, QuickMap<String, EntityOperationVariable<E>> variables,
		QuickMap<String, FieldLoadType> fieldLoadTypes, List<QueryOrder<E, ?>> order) {
		super(selection.getEntityType(), variables);
		theSelection = selection;
		theFieldLoadTypes = fieldLoadTypes;
		theOrder = order;
	}

	@Override
	public EntityCondition<E> getSelection() {
		return theSelection;
	}

	@Override
	public QuickMap<String, FieldLoadType> getFieldLoadTypes() {
		return theFieldLoadTypes;
	}

	@Override
	public List<QueryOrder<E, ?>> getOrder() {
		return theOrder;
	}

	@Override
	public ConfigurableQuery<E> loadField(ObservableEntityFieldType<E, ?> field, FieldLoadType type) {
		if (getEntityType().getFields().get(field.getFieldIndex()) != field)
			throw new IllegalArgumentException("Unrecognized field: " + field);
		QuickMap<String, FieldLoadType> loadTypes = theFieldLoadTypes.copy();
		loadTypes.put(field.getFieldIndex(), type);
		return new ConfigurableQueryImpl<>(theSelection, getVariables(), loadTypes, theOrder);
	}

	@Override
	public ConfigurableQuery<E> loadAllFields(FieldLoadType type) {
		QuickMap<String, FieldLoadType> loadTypes = theFieldLoadTypes.copy();
		for (int i = 0; i < loadTypes.keySize(); i++)
			loadTypes.put(i, type);
		return new ConfigurableQueryImpl<>(theSelection, getVariables(), loadTypes, theOrder);
	}

	@Override
	public ConfigurableQuery<E> orderBy(EntityValueAccess<E, ?> value, boolean ascending) {
		List<QueryOrder<E, ?>> order = new ArrayList<>(theOrder.size() + 1);
		boolean found = false;
		for (QueryOrder<E, ?> o : theOrder) {
			if (!found && o.getValue().equals(value)) {
				found = true;
				order.add(new QueryOrder<>(value, ascending));
			} else
				order.add(o);
		}
		order.add(new QueryOrder<>(value, ascending));
		return new ConfigurableQueryImpl<>(theSelection, getVariables(), theFieldLoadTypes, Collections.unmodifiableList(order));
	}

	@Override
	public PreparedQuery<E> prepare() throws IllegalStateException, EntityOperationException {
		return new PreparedQueryImpl<>(this,
			((ObservableEntityDataSetImpl) getEntityType().getEntitySet()).getImplementation().prepare(this), getSelection(),
			theFieldLoadTypes, getVariables().keySet().createMap());
	}

	@Override
	public ObservableValue<Long> count() throws IllegalStateException, EntityOperationException {
		if (getVariables().keySize() > 0)
			throw new IllegalStateException("This query has variables and must be prepared");
		return ((ObservableEntityTypeImpl<E>) getEntityType()).count(this, null);
	}

	@Override
	public ObservableSortedSet<E> collect(boolean withUpdates) throws IllegalStateException, EntityOperationException {
		if (getVariables().keySize() > 0)
			throw new IllegalStateException("This query has variables and must be prepared");
		return ((ObservableEntityTypeImpl<E>) getEntityType()).collect(this, withUpdates, null);
	}

	@Override
	public ObservableSortedSet<ObservableEntity<? extends E>> collectObservable(boolean withUpdates)
		throws IllegalStateException, EntityOperationException {
		if (getVariables().keySize() > 0)
			throw new IllegalStateException("This query has variables and must be prepared");
		return ((ObservableEntityTypeImpl<E>) getEntityType()).collectObservable(this, withUpdates, null);
	}

	@Override
	public ObservableSortedSet<EntityIdentity<? super E>> collectIdentities() throws IllegalStateException, EntityOperationException {
		if (getVariables().keySize() > 0)
			throw new IllegalStateException("This query has variables and must be prepared");
		return ((ObservableEntityTypeImpl<E>) getEntityType()).collectIdentities(this, null);
	}
}
