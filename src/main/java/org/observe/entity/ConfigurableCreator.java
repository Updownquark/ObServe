package org.observe.entity;

public interface ConfigurableCreator<E> extends EntityFieldSetOperation<E>, EntityCreator<E> {
	@Override
	<F> ConfigurableCreator<E> setField(ObservableEntityFieldType<? super E, F> field, F value);

	@Override
	ConfigurableCreator<E> setFieldVariable(ObservableEntityFieldType<? super E, ?> field, String variableName);

	@Override
	PreparedCreator<E> prepare() throws IllegalStateException, EntityOperationException;
}
