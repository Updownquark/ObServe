package org.observe.entity;

import org.qommons.collect.ParameterSet.ParameterMap;

public interface EntityOperation<E> {
	ObservableEntityType<E> getEntityType();

	/**
	 * A prepared operation is one that has done work before hand to optimize repeated executions
	 *
	 * @return Whether this operation has been {@link #prepare() prepared}
	 */
	boolean isPrepared();

	/**
	 * Creates a prepared operation, identical to this operation, but optimized for repeated executions
	 *
	 * @return The prepared operation
	 * @throws IllegalStateException If this is already a {@link #isPrepared() prepared} operation
	 * @throws EntityOperationException If the preparation fails
	 */
	EntityOperation<E> prepare() throws IllegalStateException, EntityOperationException;

	ParameterMap<EntityOperationVariable<E, ?>> getVariables();

	/**
	 * @param variableName The name of the variable to satisfy
	 * @param value The value with which to satisfy the variable
	 * @return A copy of this operation with the given variable satisfied
	 * @throws IllegalStateException If this is not a {@link #isPrepared() prepared} operation
	 * @throws IllegalArgumentException If the given name does not match a recognized variable in this operation, or if the given value is
	 *         incompatible with the specified variable
	 */
	EntityOperation<E> satisfy(String variableName, Object value) throws IllegalStateException, IllegalArgumentException;

	ParameterMap<Object> getVariableValues();
}
