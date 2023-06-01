package org.observe.entity;

/**
 * A prepared {@link EntityCreator}
 *
 * @param <E> The type of the query that this creator may be creating an element within
 * @param <E2> The type of entity to create
 */
public interface PreparedCreator<E, E2 extends E> extends PreparedOperation<E2>, EntityCreator<E, E2> {
	@Override
	ConfigurableCreator<E, E2> getDefinition();
}
