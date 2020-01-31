package org.observe.entity;

import java.util.function.Function;

import org.qommons.collect.QuickSet.QuickMap;

/**
 * A configurable entity operation that requires or allows setting field values ({@link ConfigurableCreator} or {@link ConfigurableUpdate})
 *
 * @param <E> The type of the entity to set fields for
 */
public interface EntityFieldSetOperation<E> extends ConfigurableOperation<E> {
	/** @return All fields whose values have been set directly in this operation */
	QuickMap<String, Object> getFieldValues();

	/** @return All fields whose values have been set to variables in this operation */
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

	/**
	 * @param <F> The type of the field
	 * @param field The getter of the field to set
	 * @param value The value for the field
	 * @return A copy of this operation that will set the given field
	 * @throws IllegalArgumentException If the given value is invalid for the given field
	 */
	default <F> EntityFieldSetOperation<E> setField(Function<? super E, F> field, F value) {
		return setField(getEntityType().getField(field), value);
	}

	/**
	 * @param field The getter of the field to set
	 * @param variableName The name of the variable to set the value to in the {@link #prepare() prepared operation}
	 * @return A copy of this operation that will set the given field
	 * @throws IllegalArgumentException If the given value is invalid for the given field
	 */
	default <F> EntityFieldSetOperation<E> setFieldVariable(Function<? super E, F> field, String variableName) {
		return setFieldVariable(getEntityType().getField(field), variableName);
	}
}
