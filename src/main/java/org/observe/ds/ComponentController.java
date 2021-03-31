package org.observe.ds;

/**
 * The {@link DSComponent} sub-type returned from {@link DependencyService#inject(String, java.util.function.Function)} and passed to the
 * component value creator function, allowing the component and/or its creator more control over the component's state in the service.
 *
 * @param <C> The type of the component value
 */
public interface ComponentController<C> extends DSComponent<C> {
	/**
	 * Sets the availability of a component. An unavailable component is not removed from the service, but it and any components that depend
	 * on it will be deactivated.
	 *
	 * @param available Whether the component should be available.
	 * @return This component
	 */
	ComponentController<C> setAvailable(boolean available);

	/**
	 * Removes this component irrevocably from the service
	 *
	 * @return This component
	 */
	ComponentController<C> remove();

	/** @return The un-controllable component */
	DSComponent<C> getComponent();
}
