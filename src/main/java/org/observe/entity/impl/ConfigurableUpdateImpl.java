package org.observe.entity.impl;

import org.observe.entity.ConfigurableUpdate;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityModificationResult;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityOperationVariable;
import org.observe.entity.EntityUpdate;
import org.observe.entity.ObservableEntityFieldType;
import org.observe.entity.PreparedUpdate;
import org.qommons.collect.QuickSet.QuickMap;

class ConfigurableUpdateImpl<E> extends AbstractConfigurableOperation<E> implements ConfigurableUpdate<E> {
	private final EntityCondition<E> theSelection;
	private final QuickMap<String, Object> theUpdateValues;
	private final QuickMap<String, EntityOperationVariable<E>> theUpdateVariables;

	ConfigurableUpdateImpl(EntityCondition<E> selection) {
		this(selection, QuickMap.of(selection.getVariables(), String::compareTo),
			selection.getEntityType().getFields().keySet().createMap().fill(EntityUpdate.NOT_SET), QuickMap.empty());
	}

	ConfigurableUpdateImpl(EntityCondition<E> selection, QuickMap<String, EntityOperationVariable<E>> variables,
		QuickMap<String, Object> updateValues, QuickMap<String, EntityOperationVariable<E>> updateVariables) {
		super(selection.getEntityType(), variables);
		theSelection = selection;
		theUpdateValues = updateValues;
		theUpdateVariables = updateVariables;
	}

	@Override
	public EntityCondition<E> getSelection() {
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
	public <F> ConfigurableUpdate<E> withField(ObservableEntityFieldType<? super E, F> field, F value)
		throws IllegalStateException, IllegalArgumentException {
		if (getEntityType().getFields().get(field.getIndex()) != field)
			throw new IllegalArgumentException("Unrecognized field " + field);
		QuickMap<String, Object> copy = theUpdateValues.copy();
		// TODO Type/constraint check
		copy.put(field.getIndex(), value);
		return new ConfigurableUpdateImpl<>(theSelection, getVariables(), copy.unmodifiable(), theUpdateVariables);
	}

	@Override
	public ConfigurableUpdate<E> withVariable(ObservableEntityFieldType<? super E, ?> field, String variableName)
		throws IllegalStateException, IllegalArgumentException {
		if (getEntityType().getFields().get(field.getIndex()) != field)
			throw new IllegalArgumentException("Unrecognized field " + field);
		QuickMap<String, EntityOperationVariable<E>> variables = getOrAddVariable(variableName);
		QuickMap<String, EntityOperationVariable<E>> copy = theUpdateVariables.copy();
		copy.put(field.getIndex(), variables.get(variableName));
		return new ConfigurableUpdateImpl<>(theSelection, variables, theUpdateValues, copy.unmodifiable());
	}

	@Override
	public PreparedUpdate<E> prepare() throws IllegalStateException, EntityOperationException {
		return new PreparedUpdateImpl<>(this,
			((ObservableEntityDataSetImpl) getEntityType().getEntitySet()).getImplementation().prepare(this),
			getVariables().keySet().createMap(), theSelection);
	}

	@Override
	public EntityModificationResult<E> execute(boolean sync, Object cause) throws IllegalStateException, EntityOperationException {
		if (getVariables().keySize() > 0)
			throw new IllegalStateException("This update has variables and must be prepared");
		return ((ObservableEntityTypeImpl<E>) getEntityType()).getEntitySet().update(this, sync, cause);
	}
}
