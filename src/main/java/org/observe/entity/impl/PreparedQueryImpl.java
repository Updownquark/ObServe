package org.observe.entity.impl;

import org.observe.entity.ConfigurableQuery;
import org.observe.entity.EntityCollectionResult;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityCountResult;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQuery;
import org.observe.entity.FieldLoadType;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.PreparedQuery;
import org.qommons.collect.QuickSet.QuickMap;

class PreparedQueryImpl<E> extends AbstractPreparedSetOperation<E, PreparedQueryImpl<E>> implements PreparedQuery<E> {
	private final QuickMap<String, FieldLoadType> theFieldLoadTypes;

	PreparedQueryImpl(ConfigurableQuery<E> definition, Object preparedObject, EntityCondition<E> selection,
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
	public EntityCountResult<E> count() throws IllegalStateException, EntityOperationException {
		if (getVariables().keySize() > 0)
			throw new IllegalStateException("This query has variables and must be prepared");
		return ((ObservableEntityTypeImpl<E>) getEntityType()).getEntitySet().count(this);
	}

	@Override
	public EntityCollectionResult<E> collect(boolean withUpdates) throws IllegalStateException, EntityOperationException {
		if (getVariables().keySize() > 0)
			throw new IllegalStateException("This query has variables and must be prepared");
		return ((ObservableEntityTypeImpl<E>) getEntityType()).getEntitySet().collect(this, withUpdates);
	}
}
