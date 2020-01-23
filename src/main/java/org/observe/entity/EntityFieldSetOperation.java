package org.observe.entity;

import org.qommons.collect.QuickSet.QuickMap;

public interface EntityFieldSetOperation<E> extends ConfigurableOperation<E> {
	QuickMap<String, Object> getFieldValues();

	QuickMap<String, EntityOperationVariable<E>> getFieldVariables();

	/**
	 * @param <F> The type of the field
	 * @param field The field to set
	 * @param value The value for the field
	 * @return A copy of this operation that will set the given field
	 * @throws IllegalArgumentException If the given value is invalid for the given field
	 */
	<F> EntityFieldSetOperation<E> setField(ObservableEntityFieldType<? super E, F> field, F value);

	/**
	 * @param field The field to set
	 * @param variableName The name of the variable to set the value to in the {@link #prepare() prepared operation}
	 * @return A copy of this operation that will set the given field
	 * @throws IllegalArgumentException If the given value is invalid for the given field
	 */
	EntityFieldSetOperation<E> setFieldVariable(ObservableEntityFieldType<? super E, ?> field, String variableName);
}
