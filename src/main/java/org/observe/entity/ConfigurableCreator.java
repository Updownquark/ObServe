package org.observe.entity;

import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.config.ConfiguredValueField;
import org.observe.config.ValueCreator;

/**
 * An un-prepared {@link EntityCreator}
 *
 * @param <E> The super-type of the collection this creator is for
 * @param <E2> The type of entity to create instances for
 */
public interface ConfigurableCreator<E, E2 extends E> extends EntityFieldSetOperation<E2>, EntityCreator<E2>, ValueCreator<E, E2> {
	@Override
	default ObservableEntityType<E2> getType() {
		return getEntityType();
	}

	@Override
	default ConfigurableCreator<E, E2> with(String fieldName, Object value) throws IllegalArgumentException {
		ValueCreator.super.with(fieldName, value);
		return this;
	}

	@Override
	default <F> ConfigurableCreator<E, E2> with(Function<? super E2, F> fieldGetter, F value) throws IllegalArgumentException {
		ValueCreator.super.with(fieldGetter, value);
		return this;
	}

	@Override
	<F> ConfigurableCreator<E, E2> with(ConfiguredValueField<E2, F> field, F value) throws IllegalArgumentException;

	@Override
	default ConfigurableCreator<E, E2> copy(E template) {
		ValueCreator.super.copy(template);
		return this;
	}

	@Override
	EntityCreationResult<E2> createAsync(Consumer<? super E2> preAddAction);

	@Override
	<F> ConfigurableCreator<E, E2> with(ObservableEntityFieldType<? super E2, F> field, F value);

	@Override
	ConfigurableCreator<E, E2> withVariable(ObservableEntityFieldType<? super E2, ?> field, String variableName);

	@Override
	default ConfigurableCreator<E, E2> withVariable(String fieldName, String variableName) {
		EntityFieldSetOperation.super.withVariable(fieldName, variableName);
		return this;
	}

	@Override
	default <F> ConfigurableCreator<E, E2> withVariable(Function<? super E2, F> field, String variableName) {
		return (ConfigurableCreator<E, E2>) EntityFieldSetOperation.super.withVariable(field, variableName);
	}

	@Override
	PreparedCreator<E2> prepare() throws IllegalStateException, EntityOperationException;
}
