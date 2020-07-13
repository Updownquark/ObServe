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

	/** @return null if this creator can currently create a new value, or a reason why it can't */
	String canCreate();

	/**
	 * Creates a new value in the collection, returning a new element containing the value
	 * 
	 * @return The element in the collection containing the new value
	 * @throws ValueOperationException If the value could not be created for any reason
	 */
	default CollectionElement<E> create() throws ValueOperationException {
		return create(null);
	}
	/**
	 * Creates a new value in the collection, returning a new element containing the value
	 * 
	 * @param preAddAction A consumer to be called with the new value before it is added to the collection
	 * @return The element in the collection containing the new value
	 * @throws ValueOperationException If the value could not be created for any reason
	 */
	CollectionElement<E> create(Consumer<? super E2> preAddAction) throws ValueOperationException;
	/**
	 * Creates a new value in the collection asynchronously, returning immediately
	 * 
	 * @param preAddAction A consumer to be called with the new value before it is added to the collection
	 * @return A result structure that will contain the result when the operation is completed, as well as status about its progress
	 */
	ObservableCreationResult<E2> createAsync(Consumer<? super E2> preAddAction);
}
