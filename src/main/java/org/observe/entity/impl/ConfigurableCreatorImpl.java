package org.observe.entity.impl;

import org.observe.entity.ConfigurableCreator;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.PreparedCreator;
import org.qommons.collect.QuickSet.QuickMap;

public class ConfigurableCreatorImpl<E> extends AbstractConfigurableOperation<E> implements ConfigurableCreator<E> {
	private final QuickMap<String, Object> theFieldValues;
	private final QuickMap<String, EntityOperationVariable<E>> theFieldVariables;

	public ConfigurableCreatorImpl(ObservableEntityType<E> entityType, QuickMap<String, EntityOperationVariable<E>> variables,
		QuickMap<String, Object> fieldValues, QuickMap<String, EntityOperationVariable<E>> fieldVariables) {
		super(entityType, variables);
		theFieldValues = fieldValues;
		theFieldVariables = fieldVariables;
	}

	@Override
	public QuickMap<String, Object> getFieldValues() {
		return theFieldValues;
	}

	@Override
	public QuickMap<String, EntityOperationVariable<E>> getFieldVariables() {
		return theFieldVariables;
	}

	@Override
	public <F> ConfigurableCreator<E> setField(ObservableEntityFieldType<? super E, F> field, F value) {
		if (getEntityType().getFields().get(field.getFieldIndex()) != field)
			throw new IllegalArgumentException("Unrecognized field: " + field);
		// TODO Type/constraint check
		QuickMap<String, Object> copy = theFieldValues.copy();
		copy.put(field.getFieldIndex(), value);
		return new ConfigurableCreatorImpl<>(getEntityType(), getVariables(), copy.unmodifiable(), theFieldVariables);
	}

	@Override
	public ConfigurableCreator<E> setFieldVariable(ObservableEntityFieldType<? super E, ?> field, String variableName) {
		if (getEntityType().getFields().get(field.getFieldIndex()) != field)
			throw new IllegalArgumentException("Unrecognized field: " + field);
		QuickMap<String, EntityOperationVariable<E>> variables = getOrAddVariable(variableName);
		QuickMap<String, EntityOperationVariable<E>> fieldVariables = theFieldVariables.copy();
		fieldVariables.put(field.getFieldIndex(), variables.get(variableName));
		return new ConfigurableCreatorImpl<>(getEntityType(), variables, theFieldValues, fieldVariables.unmodifiable());
	}

	@Override
	public PreparedCreator<E> prepare() throws IllegalStateException, EntityOperationException {
		return new PreparedCreatorImpl<>(this,
			((ObservableEntityDataSetImpl) getEntityType().getEntitySet()).getImplementation().prepare(this),
			getVariables().keySet().createMap());
	}

	@Override
	public EntityIdentity<E> create() throws IllegalStateException, EntityOperationException {
		return ((ObservableEntityTypeImpl<E>) getEntityType()).create(this, null);
	}

	@Override
	public ObservableEntity<E> createAndGet() throws IllegalStateException, EntityOperationException {
		return ((ObservableEntityTypeImpl<E>) getEntityType()).createAndGet(this, null);
	}
}
