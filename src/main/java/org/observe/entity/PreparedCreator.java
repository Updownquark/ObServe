package org.observe.entity;

public interface PreparedCreator<E> extends PreparedOperation<E>, EntityCreator<E> {
	@Override
	ConfigurableCreator<E> getDefinition();
}
