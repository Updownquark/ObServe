package org.observe.entity;

/**
 * A prepared {@link EntityCreator}
 *
 * @param <E> The type of entity to create
 */
public interface PreparedCreator<E> extends PreparedOperation<E>, EntityCreator<E> {
	@Override
	ConfigurableCreator<?, E> getDefinition();
}
