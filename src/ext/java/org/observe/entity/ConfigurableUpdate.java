package org.observe.entity;

import java.util.function.Function;

/**
 * An un-prepared {@link EntityUpdate}
 *
 * @param <E> The type of entity to update fields in
 */
public interface ConfigurableUpdate<E> extends EntityFieldSetOperation<E>, EntityUpdate<E> {
	@Override
	<F> ConfigurableUpdate<E> withField(ObservableEntityFieldType<? super E, F> field, F value)
		throws IllegalStateException, IllegalArgumentException;

	@Override
	ConfigurableUpdate<E> withVariable(ObservableEntityFieldType<? super E, ?> field, String variableName)
		throws IllegalStateException, IllegalArgumentException;

	@Override
	default <F> ConfigurableUpdate<E> with(Function<? super E, F> field, F value) {
		return (ConfigurableUpdate<E>) EntityFieldSetOperation.super.with(field, value);
	}

	@Override
	default <F> ConfigurableUpdate<E> withVariable(Function<? super E, F> field, String variableName) {
		return (ConfigurableUpdate<E>) EntityFieldSetOperation.super.withVariable(field, variableName);
	}

	@Override
	PreparedUpdate<E> prepare() throws IllegalStateException, EntityOperationException;
}
