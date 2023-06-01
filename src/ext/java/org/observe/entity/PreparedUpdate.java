package org.observe.entity;

import org.qommons.collect.QuickSet.QuickMap;

/**
 * A prepared {@link EntityUpdate}
 * 
 * @param <E> The type of entity to change the fields of
 */
public interface PreparedUpdate<E> extends PreparedOperation<E>, EntityUpdate<E> {
	@Override
	default ObservableEntityType<E> getEntityType() {
		return PreparedOperation.super.getEntityType();
	}

	@Override
	ConfigurableUpdate<E> getDefinition();

	@Override
	default QuickMap<String, Object> getFieldValues() {
		QuickMap<String, Object> updateValues = getDefinition().getFieldValues().copy();
		for (int i = 0; i < updateValues.keySize(); i++) {
			EntityOperationVariable<E> vbl = getDefinition().getFieldVariables().get(i);
			if (vbl != null)
				updateValues.put(i, getVariableValues().get(vbl.getName()));
		}
		return updateValues.unmodifiable();
	}
}
