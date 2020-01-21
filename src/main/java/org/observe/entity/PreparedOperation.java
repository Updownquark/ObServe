package org.observe.entity;

import org.qommons.collect.QuickSet.QuickMap;

public interface PreparedOperation<E> extends EntityOperation<E> {
	ConfigurableOperation<E> getDefinition();

	@Override
	default ObservableEntityType<E> getEntityType() {
		return getDefinition().getEntityType();
	}

	@Override
	default QuickMap<String, EntityOperationVariable<E>> getVariables() {
		return getDefinition().getVariables();
	}

	/**
	 * @param variableName The name of the variable to satisfy
	 * @param value The value with which to satisfy the variable
	 * @return A copy of this operation with the given variable satisfied
	 * @throws IllegalArgumentException If the given name does not match a recognized variable in this operation, or if the given value is
	 *         incompatible with the specified variable
	 */
	PreparedOperation<E> satisfy(String variableName, Object value) throws IllegalArgumentException;

	QuickMap<String, Object> getVariableValues();
}
