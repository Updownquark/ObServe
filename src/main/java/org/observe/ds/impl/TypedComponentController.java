package org.observe.ds.impl;

import org.observe.ds.ComponentController;

/**
 * A {@link ComponentController} for a component in a {@link DefaultTypedDependencyService}
 * 
 * @param <C> The component type of the service
 */
public interface TypedComponentController<C> extends ComponentController<C> {
	@Override
	TypedDSComponent<C> getComponent();
}
