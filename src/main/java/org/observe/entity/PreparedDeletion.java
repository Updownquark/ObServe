package org.observe.entity;

/**
 * A prepared {@link EntityDeletion}
 * 
 * @param <E> The type of entity to delete
 */
public interface PreparedDeletion<E> extends PreparedOperation<E>, EntityDeletion<E> {
	@Override
	default ObservableEntityType<E> getEntityType() {
		return PreparedOperation.super.getEntityType();
	}

	@Override
	ConfigurableDeletion<E> getDefinition();

	@Override
	PreparedDeletion<E> satisfy(String variableName, Object value) throws IllegalArgumentException;
}
