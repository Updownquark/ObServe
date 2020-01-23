package org.observe.entity;

/**
 * An un-prepared {@link EntityUpdate}
 * 
 * @param <E> The type of entity to update fields in
 */
public interface ConfigurableUpdate<E> extends EntityFieldSetOperation<E>, EntityUpdate<E> {
	@Override
	<F> ConfigurableUpdate<E> setField(ObservableEntityFieldType<? super E, F> field, F value)
		throws IllegalStateException, IllegalArgumentException;

	@Override
	ConfigurableUpdate<E> setFieldVariable(ObservableEntityFieldType<? super E, ?> field, String variableName)
		throws IllegalStateException, IllegalArgumentException;

	@Override
	PreparedUpdate<E> prepare() throws IllegalStateException, EntityOperationException;
}
