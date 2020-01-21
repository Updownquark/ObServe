package org.observe.entity;

import org.qommons.collect.QuickSet.QuickMap;

public interface ConfigurableCreator<E> extends ConfigurableOperation<E>, EntityCreator<E> {
	QuickMap<String, Object> getFieldValues();

	QuickMap<String, EntityOperationVariable<E>> getFieldVariables();

	<F> ConfigurableCreator<E> with(ObservableEntityFieldType<? super E, F> field, F value);

	ConfigurableCreator<E> withVariable(ObservableEntityFieldType<? super E, ?> field, String variableName);

	@Override
	PreparedCreator<E> prepare() throws IllegalStateException, EntityOperationException;
}
