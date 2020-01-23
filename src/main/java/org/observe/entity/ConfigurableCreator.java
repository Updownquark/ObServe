package org.observe.entity;

/**
 * An un-prepared {@link EntityCreator}
 * 
 * @param <E> The type of entity to create instances for
 */
public interface ConfigurableCreator<E> extends EntityFieldSetOperation<E>, EntityCreator<E> {
	@Override
	<F> ConfigurableCreator<E> setField(ObservableEntityFieldType<? super E, F> field, F value);

	@Override
	ConfigurableCreator<E> setFieldVariable(ObservableEntityFieldType<? super E, ?> field, String variableName);

	@Override
	PreparedCreator<E> prepare() throws IllegalStateException, EntityOperationException;
}
