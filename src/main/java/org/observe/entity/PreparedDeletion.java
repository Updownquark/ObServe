package org.observe.entity;

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
