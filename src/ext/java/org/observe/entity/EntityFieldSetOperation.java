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
	<F> EntityFieldSetOperation<E> withField(ObservableEntityFieldType<? super E, F> field, F value);

	/**
	 * @param field The field to set
	 * @param variableName The name of the variable to set the value to in the {@link #prepare() prepared operation}
	 * @return A copy of this operation that will set the given field
	 * @throws IllegalArgumentException If the given value is invalid for the given field
	 */
	EntityFieldSetOperation<E> withVariable(ObservableEntityFieldType<? super E, ?> field, String variableName);

	/**
	 * @param fieldName The name of the field to set
	 * @param value The value for the field
	 * @return This operation
	 */
	default EntityFieldSetOperation<E> with(String fieldName, Object value) {
		return withField((ObservableEntityFieldType<E, Object>) getEntityType().getFields().get(fieldName), value);
	}

	/**
	 * @param <F> The type of the field
	 * @param field The getter of the field to set
	 * @param value The value for the field
	 * @return A copy of this operation that will set the given field
	 * @throws IllegalArgumentException If the given value is invalid for the given field
	 */
	default <F> EntityFieldSetOperation<E> with(Function<? super E, F> field, F value) {
		return withField(getEntityType().getField(field), value);
	}

	/**
	 * @param fieldName The name of the field to set
	 * @param variableName The variable name to assign the field to
	 * @return This operation
	 */
	default EntityFieldSetOperation<E> withVariable(String fieldName, String variableName) {
		return withVariable(getEntityType().getFields().get(fieldName), variableName);
	}

	/**
	 * @param field The getter of the field to set
	 * @param variableName The name of the variable to set the value to in the {@link #prepare() prepared operation}
	 * @return A copy of this operation that will set the given field
	 * @throws IllegalArgumentException If the given value is invalid for the given field
	 */
	default <F> EntityFieldSetOperation<E> withVariable(Function<? super E, F> field, String variableName) {
		return withVariable(getEntityType().getField(field), variableName);
	}
}
