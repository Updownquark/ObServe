package org.observe.entity;

import org.qommons.collect.QuickSet.QuickMap;

/**
 * A prepared entity operation. Prepared operations may be faster than un-prepared ones.
 *
 * @param <E> The type of entity to operate on
 */
public interface PreparedOperation<E> extends EntityOperation<E> {
	/** @return The configured operation that this is a preparation of */
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

	/** @return The values of all variables in this operation */
	QuickMap<String, Object> getVariableValues();
}
