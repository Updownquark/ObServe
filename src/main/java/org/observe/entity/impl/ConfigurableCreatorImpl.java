package org.observe.entity.impl;

import org.observe.entity.ConfigurableCreator;
import org.observe.entity.EntityCreationResult;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.PreparedCreator;
import org.qommons.collect.QuickSet.QuickMap;

class ConfigurableCreatorImpl<E, E2 extends E> extends AbstractConfigurableOperation<E2> implements ConfigurableCreator<E, E2> {
	private final QuickMap<String, Object> theFieldValues;
	private final QuickMap<String, EntityOperationVariable<E2>> theFieldVariables;

	ConfigurableCreatorImpl(ObservableEntityType<E2> entityType, QuickMap<String, EntityOperationVariable<E2>> variables,
		QuickMap<String, Object> fieldValues, QuickMap<String, EntityOperationVariable<E2>> fieldVariables) {
		super(entityType, variables);
		theFieldValues = fieldValues;
		theFieldVariables = fieldVariables;
	}

	@Override
	public QuickMap<String, Object> getFieldValues() {
		return theFieldValues;
	}

	@Override
	public QuickMap<String, EntityOperationVariable<E2>> getFieldVariables() {
		return theFieldVariables;
	}

	@Override
	public <F> ConfigurableCreator<E, E2> with(ObservableEntityFieldType<? super E2, F> field, F value) {
		if (getEntityType().getFields().get(field.getIndex()) != field)
			throw new IllegalArgumentException("Unrecognized field: " + field);
		// TODO Type/constraint check
		QuickMap<String, Object> copy = theFieldValues.copy();
		copy.put(field.getIndex(), value);
		return new ConfigurableCreatorImpl<>(getEntityType(), getVariables(), copy.unmodifiable(), theFieldVariables);
	}

	@Override
	public ConfigurableCreator<E, E2> withVariable(ObservableEntityFieldType<? super E2, ?> field, String variableName) {
		if (getEntityType().getFields().get(field.getIndex()) != field)
			throw new IllegalArgumentException("Unrecognized field: " + field);
		QuickMap<String, EntityOperationVariable<E2>> variables = getOrAddVariable(variableName);
		QuickMap<String, EntityOperationVariable<E2>> fieldVariables = theFieldVariables.copy();
		fieldVariables.put(field.getIndex(), variables.get(variableName));
		return new ConfigurableCreatorImpl<>(getEntityType(), variables, theFieldValues, fieldVariables.unmodifiable());
	}

	@Override
	public PreparedCreator<E2> prepare() throws IllegalStateException, EntityOperationException {
		return new PreparedCreatorImpl<>(this,
			((ObservableEntityDataSetImpl) getEntityType().getEntitySet()).getImplementation().prepare(this),
			getVariables().keySet().createMap());
	}

	@Override
	public EntityCreationResult<E2> create(boolean sync, Object cause) throws IllegalStateException, EntityOperationException {
		return ((ObservableEntityTypeImpl<E2>) getEntityType()).getEntitySet().create(this, sync, cause);
	}
}
