package org.observe.ds;

/**
 * The stage of a {@link DSComponent}. Components can follow several stage flows. Initially, this will be one of:
 * <ul>
 * <li>{@link #Defined} (optional)-->{@link #Satisfied}-->{@link #Complete}. This signifies that all of a component's required dependencies
 * are satisfied among the set of components defined initially.</li>
 * <li>{@link #Defined}-->{@link #Unsatisfied}. This signifies that one or more required dependencies are not satisfied among the set of
 * components defined initially.</li>
 * </ul>
 * <p>
 * After the initial set of components, new components can be {@link DependencyService#inject(String, java.util.function.Function)
 * injected}, which could cause {@link #Unsatisfied}-->{@link #Complete}.
 * </p>
 * <p>
 * Additionally, components defined in the initial set or injected later may be {@link ComponentController#setAvailable(boolean) made
 * unavailable} or {@link ComponentController#remove() removed} entirely, which may cause {@link #Complete}-->{@link #Unsatisfied}.
 * </p>
 * <p>
 * When new providers for already-satisfied dependencies with variable multiplicity are injected or made available, an update event will be
 * fired from {@link DSComponent#getStage()} with the {@link #Satisfied} or {@link #Complete} stage.
 * </p>
 */
public enum ComponentStage {
	/**
	 * The component has been defined in the framework, but has some required dependencies that are not satisfied--initial stage, before the
	 * initial set of components has been completely defined and the service has been initialized.
	 */
	Defined,
	/**
	 * All of the component's required, non-{@link Dependency#isDynamic() dynamic} dependencies have been satisfied by components that are
	 * themselves at least {@link #PreSatisfied pre-satisfied}. Required dynamic dependencies, if present, shall be satisfied shortly.
	 *
	 * Components that are pre-satisfied may be presented to components that are themselves {@link #Satisfied}, therefore any component with
	 * dynamic dependencies that reaches this stage must behave and perform its essential capabilities before it is completely
	 * {@link #Satisfied}.
	 */
	PreSatisfied,
	/**
	 * All of the component's required dependencies have been satisfied by components that are themselves at least {@link #PreSatisfied
	 * pre-satisfied}. Dependencies with variable multiplicities may have additional providers added after this.
	 */
	Satisfied,
	/**
	 * Same as {@link #Satisfied}, but after the initial set of components have been completely defined and the service has finished
	 * initializing
	 */
	Complete,
	/**
	 * The component has unsatisfied required dependencies and the initial set of components has been completely defined. This is also the
	 * stage for components that are {@link DSComponent#isAvailable() unavailable} components.
	 */
	Unsatisfied,
	/** The component has been {@link ComponentController#remove() removed} */
	Removed;

	/** @return Whether a component at this stage should be active in the dependency service */
	public boolean isActive() {
		switch (this) {
		case PreSatisfied:
		case Satisfied:
		case Complete:
			return true;
		default:
			return false;
		}
	}
}
