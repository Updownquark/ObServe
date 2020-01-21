package org.observe.entity.impl;

import org.observe.ObservableValue;
import org.observe.collect.ObservableSortedSet;
import org.observe.entity.ConfigurableQuery;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQuery;
import org.observe.entity.EntitySelection;
import org.observe.entity.FieldLoadType;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.PreparedQuery;
import org.qommons.collect.QuickSet.QuickMap;

public class PreparedQueryImpl<E> extends AbstractPreparedSetOperation<E, PreparedQueryImpl<E>> implements PreparedQuery<E> {
	private final QuickMap<String, FieldLoadType> theFieldLoadTypes;

	PreparedQueryImpl(ConfigurableQuery<E> definition, Object preparedObject, EntitySelection<E> selection,
		QuickMap<String, FieldLoadType> loadTypes, QuickMap<String, Object> variables) {
		super(definition, preparedObject, variables, selection);
		theFieldLoadTypes = loadTypes;
	}

	@Override
	public ConfigurableQuery<E> getDefinition() {
		return (ConfigurableQuery<E>) super.getDefinition();
	}

	@Override
	public QuickMap<String, FieldLoadType> getFieldLoadTypes() {
		return theFieldLoadTypes;
	}

	@Override
	public EntityQuery<E> loadField(ObservableEntityFieldType<E, ?> field, FieldLoadType type) {
		if (getEntityType().getFields().get(field.getFieldIndex()) != field)
			throw new IllegalArgumentException("Unrecognized field: " + field);
		QuickMap<String, FieldLoadType> loadTypes = theFieldLoadTypes.copy();
		loadTypes.put(field.getFieldIndex(), type);
		return new PreparedQueryImpl<>(getDefinition(), getPreparedObject(), getSelection(), loadTypes, getVariableValues());
	}

	@Override
	public EntityQuery<E> loadAllFields(FieldLoadType type) {
		QuickMap<String, FieldLoadType> loadTypes = theFieldLoadTypes.copy();
		for (int i = 0; i < loadTypes.keySize(); i++)
			loadTypes.put(i, type);
		return new PreparedQueryImpl<>(getDefinition(), getPreparedObject(), getSelection(), loadTypes, getVariableValues());
	}

	@Override
	public ObservableValue<Long> count() throws IllegalStateException, EntityOperationException {
		if (getVariables().keySize() > 0)
			throw new IllegalStateException("This query has variables and must be prepared");
		return ((ObservableEntityTypeImpl<E>) getEntityType()).count(this, null);
	}

	@Override
	public ObservableSortedSet<E> collect() throws IllegalStateException, EntityOperationException {
		if (getVariables().keySize() > 0)
			throw new IllegalStateException("This query has variables and must be prepared");
		return ((ObservableEntityTypeImpl<E>) getEntityType()).collect(this, null);
	}

	@Override
	public ObservableSortedSet<ObservableEntity<? extends E>> collectObservable(boolean withUpdates)
		throws IllegalStateException, EntityOperationException {
		if (getVariables().keySize() > 0)
			throw new IllegalStateException("This query has variables and must be prepared");
		return ((ObservableEntityTypeImpl<E>) getEntityType()).collectObservable(this, null);
	}

	@Override
	public ObservableSortedSet<EntityIdentity<? super E>> collectIdentities() throws IllegalStateException, EntityOperationException {
		if (getVariables().keySize() > 0)
			throw new IllegalStateException("This query has variables and must be prepared");
		return ((ObservableEntityTypeImpl<E>) getEntityType()).collectIdentities(this, null);
	}
}
