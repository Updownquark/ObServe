package org.observe.entity;

import java.util.function.Function;

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
	default <F> ConfigurableUpdate<E> setField(Function<? super E, F> field, F value) {
		return (ConfigurableUpdate<E>) EntityFieldSetOperation.super.setField(field, value);
	}

	@Override
	default <F> ConfigurableUpdate<E> setFieldVariable(Function<? super E, F> field, String variableName) {
		return (ConfigurableUpdate<E>) EntityFieldSetOperation.super.setFieldVariable(field, variableName);
	}

	@Override
	PreparedUpdate<E> prepare() throws IllegalStateException, EntityOperationException;
}
