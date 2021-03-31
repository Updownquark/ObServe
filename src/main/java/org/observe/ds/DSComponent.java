package org.observe.ds;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection;
import org.qommons.Named;

/**
 * A component in a {@link DependencyService}
 *
 * @param <C> The type of the component value
 */
public interface DSComponent<C> extends Named {
	/** @return The value of this component, or null if it is not active */
	C getComponentValue();

	/** @return The set of services provided by this component */
	Set<? extends Service<?>> getProvided();

	/**
	 * @param <S> The type of the service
	 * @param service The service provided by this component
	 * @return The value provided by this component for the service
	 * @throws IllegalArgumentException If the given service is not provided by this component
	 */
	<S> S provide(Service<S> service) throws IllegalArgumentException;

	/** @return All dependencies of this component */
	Map<Service<?>, ? extends Dependency<C, ?>> getDependencies();
	/**
	 * @param <S> The type of the service
	 * @param service The service dependency of this component
	 * @return All provided values satisfying the dependency
	 */
	default <S> ObservableCollection<S> getDependencies(Service<S> service) {
		Dependency<C, ?> dep = getDependencies().get(service);
		if (dep == null)
			throw new IllegalArgumentException("No such dependency defined: " + getName() + "<--" + service.getName());
		return ((Dependency<C, S>) dep).get();
	}
	/**
	 * @param <S> The type of the service
	 * @param service The service dependency of this component
	 * @return The first provided value satisfying the dependency, or null if the dependency is not satisfied at all
	 */
	default <S> S getDependency(Service<S> service) {
		return getDependencies(service).peekFirst();
	}

	/**
	 * @return The stage of this component.
	 * @see ComponentStage for potential stage flows
	 */
	ObservableValue<ComponentStage> getStage();

	/** @return An observable value for whether the component is currently {@link ComponentController#setAvailable(boolean) available} */
	ObservableValue<Boolean> isAvailable();

	/**
	 * Allows configuration of a component before it is built
	 *
	 * @param <C> The type of the component value
	 */
	public interface Builder<C> extends Named {
		/**
		 * Tells the service that this component provides a service
		 *
		 * @param <S> The type of the service provided
		 * @param service The service provided
		 * @param provided Produces a service value from the component value. This value is not cached, so this method should be a simple
		 *        accessor or something similarly light and fast
		 * @return This builder
		 */
		<S> Builder<C> provides(Service<S> service, Function<? super C, ? extends S> provided);

		/**
		 * Tells the service that this component depends on a service
		 *
		 * @param <S> The type of the service depended on
		 * @param service The service depended on
		 * @param dependency Configures the dependency
		 * @return This builder
		 */
		<S> Builder<C> depends(Service<S> service, Consumer<Dependency.Builder<C, S>> dependency);

		/**
		 * @param available Whether the component should be {@link DSComponent#isAvailable() available} when it is first created
		 * @return This builder
		 */
		Builder<C> initiallyAvailable(boolean available);

		/**
		 * @param dispose A function to dispose of the component value when the component goes from active to inactive. If this consumer is
		 *        not provided (or is null), the component value will be preserved while inactive and re-used when reactivated.
		 * @return This builder
		 */
		Builder<C> disposeWhenInactive(Consumer<? super C> dispose);

		/** @return The component value producer passed to {@link DependencyService#inject(String, Function)} */
		Function<? super ComponentController<C>, ? extends C> getSupplier();

		/**
		 * @return The map of provided service to service value providers for all services provided via {@link #provides(Service, Function)}
		 */
		Map<Service<?>, Function<? super C, ?>> getProvided();

		/**
		 * @return The map of depended-on services to dependencies for all dependencies specified via {@link #depends(Service, Consumer)}
		 */
		Map<Service<?>, ? extends Dependency<C, ?>> getDependencies();

		/**
		 * @return Whether the component should be {@link DSComponent#isAvailable() available} when it is first created
		 * @see #initiallyAvailable(boolean)
		 */
		boolean isInitiallyAvailable();

		/** @return The dispose function (if any) provided via {@link #disposeWhenInactive(Consumer)} */
		Consumer<? super C> isDisposedWhenInactive();

		/** @return The controller for the new component */
		ComponentController<C> build();
	}
}
