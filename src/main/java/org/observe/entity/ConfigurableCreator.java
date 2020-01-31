package org.observe.entity;

import java.util.function.Function;

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
	default <F> ConfigurableCreator<E> setField(Function<? super E, F> field, F value) {
		return (ConfigurableCreator<E>) EntityFieldSetOperation.super.setField(field, value);
	}

	@Override
	default <F> ConfigurableCreator<E> setFieldVariable(Function<? super E, F> field, String variableName) {
		return (ConfigurableCreator<E>) EntityFieldSetOperation.super.setFieldVariable(field, variableName);
	}

	@Override
	PreparedCreator<E> prepare() throws IllegalStateException, EntityOperationException;
}
