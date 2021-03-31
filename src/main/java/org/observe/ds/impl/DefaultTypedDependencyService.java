package org.observe.ds.impl;

import java.util.Map;
import java.util.function.Function;

import org.observe.collect.ObservableCollection;
import org.observe.ds.ComponentController;
import org.observe.ds.DSComponent;
import org.observe.ds.DependencyService;
import org.observe.ds.Service;
import org.qommons.Transactable;

/**
 * A {@link DependencyService} implementation whose services types are mapped to actual java types
 *
 * @param <C> The component type of the service
 */
public class DefaultTypedDependencyService<C> extends DefaultDependencyService<C> {
	/** @param lock The thread safety controller */
	public DefaultTypedDependencyService(Transactable lock) {
		super(lock);
	}

	@Override
	public DSComponent.Builder<C> inject(String componentName, Function<? super ComponentController<C>, ? extends C> component) {
		return new DefaultTypedDSComponent.Builder<>(super.inject(componentName, component));
	}

	@Override
	public ObservableCollection<? extends TypedDSComponent<C>> getComponents() {
		return super.getComponents().flow().transform((Class<TypedDSComponent<C>>) (Class<?>) TypedDSComponent.class,
			tx -> tx.cache(false).map(c -> (TypedDSComponent<C>) c)).collect();
	}

	@Override
	protected DefaultComponent<C> createComponent(DSComponent.Builder<C> builder) {
		return new DefaultTypedDSComponent<>(this, builder.getName(), builder.getSupplier(), builder.isDisposedWhenInactive(),
			builder.getProvided(), (Map<Service<?>, ? extends DefaultDependency<C, ?>>) builder.getDependencies(),
			builder.isInitiallyAvailable());
	}
}
