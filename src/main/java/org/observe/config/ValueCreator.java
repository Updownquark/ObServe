package org.observe.config;

import java.util.function.Consumer;

import org.qommons.collect.CollectionElement;

/**
 * Creates a value in a set of values
 *
 * @param <E> The type of the value set
 * @param <E2> The sub-type of the value to create
 */
public interface ValueCreator<E, E2 extends E> {
	/** @return The {@link ConfiguredValueType} of the value to create */
	ConfiguredValueType<E2> getType();

	String canCreate();

	default CollectionElement<E> create() throws ValueOperationException {
		return create(null);
	}

	CollectionElement<E> create(Consumer<? super E2> preAddAction) throws ValueOperationException;

	ObservableCreationResult<E2> createAsync(Consumer<? super E2> preAddAction);
}
