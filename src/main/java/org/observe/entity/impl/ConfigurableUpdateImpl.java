package org.observe.entity.impl;

import org.observe.entity.ConfigurableUpdate;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.EntitySelection;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.PreparedUpdate;
import org.qommons.collect.QuickSet.QuickMap;

class ConfigurableUpdateImpl<E> extends AbstractConfigurableOperation<E> implements ConfigurableUpdate<E> {
	private final EntitySelection<E> theSelection;
	private final QuickMap<String, Object> theUpdateValues;
	private final QuickMap<String, EntityOperationVariable<E>> theUpdateVariables;

	ConfigurableUpdateImpl(EntitySelection<E> selection) {
		this(selection, QuickMap.of(selection.getVariables(), String::compareTo),
			selection.getEntityType().getFields().keySet().createMap(), QuickMap.empty());
	}

	ConfigurableUpdateImpl(EntitySelection<E> selection, QuickMap<String, EntityOperationVariable<E>> variables,
		QuickMap<String, Object> updateValues, QuickMap<String, EntityOperationVariable<E>> updateVariables) {
		super(selection.getEntityType(), variables);
		theSelection = selection;
		theUpdateValues = updateValues;
		theUpdateVariables = updateVariables;
	}

	@Override
	public EntitySelection<E> getSelection() {
		return theSelection;
	}

	@Override
	public QuickMap<String, Object> getFieldValues() {
		return theUpdateValues;
	}

	@Override
	public QuickMap<String, EntityOperationVariable<E>> getFieldVariables() {
		return theUpdateVariables;
	}

	@Override
	public <F> ConfigurableUpdate<E> setField(ObservableEntityFieldType<? super E, F> field, F value)
		throws IllegalStateException, IllegalArgumentException {
		if (getEntityType().getFields().get(field.getFieldIndex()) != field)
			throw new IllegalArgumentException("Unrecognized field " + field);
		QuickMap<String, Object> copy = theUpdateValues.copy();
		// TODO Type/constraint check
		copy.put(field.getFieldIndex(), value);
		return new ConfigurableUpdateImpl<>(theSelection, getVariables(), copy.unmodifiable(), theUpdateVariables);
	}

	@Override
	public ConfigurableUpdate<E> setFieldVariable(ObservableEntityFieldType<? super E, ?> field, String variableName)
		throws IllegalStateException, IllegalArgumentException {
		if (getEntityType().getFields().get(field.getFieldIndex()) != field)
			throw new IllegalArgumentException("Unrecognized field " + field);
		QuickMap<String, EntityOperationVariable<E>> variables = getOrAddVariable(variableName);
		QuickMap<String, EntityOperationVariable<E>> copy = theUpdateVariables.copy();
		copy.put(field.getFieldIndex(), variables.get(variableName));
		return new ConfigurableUpdateImpl<>(theSelection, variables, theUpdateValues, copy.unmodifiable());
	}

	@Override
	public PreparedUpdate<E> prepare() throws IllegalStateException, EntityOperationException {
		return new PreparedUpdateImpl<>(this,
			((ObservableEntityDataSetImpl) getEntityType().getEntitySet()).getImplementation().prepare(this),
			getVariables().keySet().createMap(), theSelection);
	}

	@Override
	public long execute() throws IllegalStateException, EntityOperationException {
		if (getVariables().keySize() > 0)
			throw new IllegalStateException("This update has variables and must be prepared");
		return ((ObservableEntityTypeImpl<E>) getEntityType()).update(this, null);
	}
}
