package org.observe.ds;

import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;

/**
 * A service containing any number of {@link DSComponent}s, each of which may provide and/or depend on services. This service can match up
 * those services with each other, allowing disparate components with no knowledge of each other to interact.
 *
 * @param <C> The type of values associated with components in this service
 */
public interface DependencyService<C> extends AutoCloseable {
	/**
	 * @param componentName The name of the component to create
	 * @param component The function to create a component value for the component when active
	 * @return A builder to allow configuration of the component, including provided services, dependencies, etc.
	 */
	DSComponent.Builder<C> inject(String componentName, Function<? super ComponentController<C>, ? extends C> component);

	/**
	 * @return All components that have been {@link #inject(String, Function) injected} into this service and have not been
	 *         {@link ComponentController#remove() removed}, including {@link DSComponent#isAvailable() unavailable} ones
	 */
	ObservableCollection<? extends DSComponent<C>> getComponents();

	/**
	 * @return All services provided by {@link ComponentStage#isActive() active}, {@link DSComponent#isAvailable() available}
	 *         {@link #getComponents() components} in this service
	 */
	ObservableSet<Service<?>> getServices();

	/**
	 * @return Whether this service has been initialized, meaning the initial set of components has completed loading. This affects whether
	 *         components may have {@link DSComponent#getStage() stages} of {@link ComponentStage#Defined} and
	 *         {@link ComponentStage#Satisfied}, as opposed to {@link ComponentStage#Unsatisfied} and {@link ComponentStage#Complete}.
	 */
	ObservableValue<Boolean> isInitialized();

	/**
	 * Schedules a task to run. Actions that involve injecting new components or changing availability of components cannot be performed as
	 * a result of a change to a component, so this method schedules those tasks to be run when they can be.
	 *
	 * @param task The task to run when component changes may be made
	 */
	void schedule(Runnable task);

	/** Causes all components in this service to be deactivated and removed */
	@Override
	void close();
}
