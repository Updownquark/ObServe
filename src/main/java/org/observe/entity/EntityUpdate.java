package org.observe.entity;

public interface EntityUpdate<E> extends EntityModification<E> {
	/**
	 * @param <F> The type of the field
	 * @param field The field to set
	 * @param value The new value for the field
	 * @return A copy of this operation that will set the given field
	 * @throws IllegalStateException If this operation has already been {@link #prepare() prepared}
	 * @throws IllegalArgumentException If the given value is invalid for the given field
	 */
	<F> EntityUpdate<E> set(ObservableEntityFieldType<? super E, F> field, F value) throws IllegalStateException, IllegalArgumentException;

	/**
	 * @param field The field to set
	 * @param variableName The name of the variable to create
	 * @return A copy of this operation that will set the given field
	 * @throws IllegalStateException If this operation has already been {@link #prepare() prepared}
	 * @throws IllegalArgumentException If the given value is invalid for the given field
	 */
	EntityUpdate<E> setVariable(ObservableEntityFieldType<? super E, ?> field, String variableName)
		throws IllegalStateException, IllegalArgumentException;

	@Override
	EntityUpdate<E> prepare() throws IllegalStateException, EntityOperationException;

	@Override
	EntityUpdate<E> satisfy(String variableName, Object value) throws IllegalStateException, IllegalArgumentException;
}
