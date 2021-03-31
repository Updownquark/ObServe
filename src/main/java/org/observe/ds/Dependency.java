package org.observe.ds;

import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;

/**
 * A dependency of a component on a service
 *
 * @param <C> The type of the component value
 * @param <S> The type of the service the component depends on
 */
public interface Dependency<C, S> {
	/** @return The component that is depending on a service */
	DSComponent<C> getOwner();

	/** @return The service that the component depends on */
	Service<S> getTarget();

	/**
	 * @return The minimum number of providers of the service needed to satisfy the dependency. Typical values are 0 (optional) or 1
	 *         (required)
	 */
	int getMinimum();

	/**
	 * @return Whether this dependency is dynamic. A dynamic dependency allows its component to be {@link ComponentStage#PreSatisfied}
	 *         before the dependency is satisfied. This is distinct from having a zero {@link #getMinimum() minimum} in that the dependency
	 *         service will only satisfy a component with a dynamic dependency after it determines that the dependency <b>will</b> be
	 *         satisfied to its {@link #getMinimum() minimum}.
	 */
	boolean isDynamic();

	/** @return All active components providing the target service */
	ObservableSet<? extends DSComponent<C>> getProviders();

	/** @return All provided services satisfying the dependency */
	ObservableCollection<S> get();

	/**
	 * Allows configuration of a dependency before it is built
	 *
	 * @param <C> The component value type
	 * @param <S> The type of the service dependency
	 */
	public interface Builder<C, S> {
		/** @return The service that the component depends on */
		Service<S> getTarget();

		/**
		 * @param min The {@link Dependency#getMinimum() minimum} for the dependency
		 * @return This builder
		 */
		Builder<C, S> minimum(int min);

		/**
		 * @param dynamic Whether the dependency should be {@link Dependency#isDynamic() dynamic}
		 * @return This builder
		 */
		Builder<C, S> dynamic(boolean dynamic);

		/**
		 * @return The {@link Dependency#getMinimum() minimum} of the dependency
		 */
		int getMinimum();

		/** @return Whether the dependency is {@link Dependency#isDynamic() dynamic} */
		boolean isDynamic();
	}
}
