package org.observe.entity;

import java.util.function.Consumer;

import org.observe.config.ValueCreator;
import org.observe.config.ValueOperationException;
import org.qommons.collect.CollectionElement;

/**
 * An operation to create a new instance of an entity type
 *
 * @param <E> The type of the query collection that this creator may be creating an element within
 * @param <E2> The entity type to create an instance for
 */
public interface EntityCreator<E, E2 extends E> extends EntityOperation<E2>, ValueCreator<E, E2> {
	/**
	 * The prefix of a message returned by {@link #canCreate()} in the case that the creator was created by an
	 * {@link EntityCollectionResult}'s {@link ObservableEntitySet#create()} method and an entity that would be created by this creator
	 * would not be present in the query due to its {@link EntityQuery#getSelection() condition}.
	 */
	public static final String QUERY_CONDITION_UNMATCHED = "Entity would not be present in query";

	@Override
	default ObservableEntityType<E2> getType() {
		return getEntityType();
	}

	@Override
	default CollectionElement<E> create() throws EntityOperationException {
		try {
			return ValueCreator.super.create();
		} catch (ValueOperationException e) {
			throw (EntityOperationException) e;
		}
	}

	@Override
	CollectionElement<E> create(Consumer<? super E2> preAddAction) throws EntityOperationException;

	@Override
	EntityCreationResult<E2> createAsync(Consumer<? super E2> preAddAction);

	/**
	 * Creates a new entity with this creator's configured field values
	 *
	 * @param sync Whether to execute the operation synchronously (blocking until completion or failure) or asynchronously
	 * @param cause The cause of the change, if any
	 * @param preAdd Accepts the new value before adding to the entity set
	 * @return The result containing new entity
	 * @throws IllegalStateException If this creation has variables but has not been prepared or has unsatisfied variables
	 * @throws EntityOperationException If the creation fails immediately
	 */
	EntityCreationResult<E2> create(boolean sync, Object cause, Consumer<? super ObservableEntity<E2>> preAdd)
		throws IllegalStateException, EntityOperationException;
}
