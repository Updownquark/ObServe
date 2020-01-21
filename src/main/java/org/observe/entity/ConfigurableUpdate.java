package org.observe.entity;

import org.qommons.collect.QuickSet.QuickMap;

public interface ConfigurableUpdate<E> extends ConfigurableOperation<E>, EntityUpdate<E> {
	QuickMap<String, EntityOperationVariable<E>> getUpdateFieldVariables();

	/**
	 * @param <F> The type of the field
	 * @param field The field to set
	 * @param value The new value for the field
	 * @return A copy of this operation that will set the given field
	 * @throws IllegalStateException If this operation has already been {@link #prepare() prepared}
	 * @throws IllegalArgumentException If the given value is invalid for the given field
	 */
	<F> ConfigurableUpdate<E> set(ObservableEntityFieldType<? super E, F> field, F value)
		throws IllegalStateException, IllegalArgumentException;

	/**
	 * @param field The field to set
	 * @param variableName The name of the variable to create
	 * @return A copy of this operation that will set the given field
	 * @throws IllegalStateException If this operation has already been {@link #prepare() prepared}
	 * @throws IllegalArgumentException If the given value is invalid for the given field
	 */
	ConfigurableUpdate<E> setVariable(ObservableEntityFieldType<? super E, ?> field, String variableName)
		throws IllegalStateException, IllegalArgumentException;

	@Override
	PreparedUpdate<E> prepare() throws IllegalStateException, EntityOperationException;
}
