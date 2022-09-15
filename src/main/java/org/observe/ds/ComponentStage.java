package org.observe.ds;

/**
 * The stage of a {@link DSComponent}. Components can follow several stage flows. Initially, this will be one of:
 * <ul>
 * <li>{@link #Defined}-->{@link #PreSatisfied}(if needed to satisfy dynamic dependency cycles)-->{@link #Satisfied}. This signifies that
 * all of a component's dependencies are satisfied among the set of components currently defined.</li>
 * <li>{@link #Defined}-->{@link #Unsatisfied}. This signifies that one or more dependencies are not satisfied among the set of components
 * currently defined.</li>
 * </ul>
 * <p>
 * After the initial set of components, new components can be {@link DependencyService#inject(String, java.util.function.Function)
 * injected}, which may cause {@link #Unsatisfied}-->{@link #Satisfied} among components whose dependencies all become satisfied.
 * </p>
 * <p>
 * Additionally, components defined in the initial set or injected later may be {@link ComponentController#setAvailable(boolean) made
 * unavailable} or {@link ComponentController#remove() removed} entirely, which may cause {@link #Satisfied}-->{@link #Unsatisfied}.
 * </p>
 * <p>
 * When new providers for already-satisfied dependencies with variable multiplicity are injected or made available, an update event will be
 * fired from {@link DSComponent#getStage()} with the {@link #Satisfied} or {@link #Satisfied} stage.
 * </p>
 */
public enum ComponentStage {
	/**
	 * The component has been defined in the framework, but has some required dependencies that are not satisfied--initial stage, before the
	 * initial set of components has been completely defined and the service has been initialized.
	 */
	Defined,
	/** The component has unsatisfied dependencies and the initial set of components has been completely defined. */
	Unsatisfied,
	/**
	 * Same as {@link #Satisfied}, but when the component itself is {@link DSComponent#isAvailable() unavailable}. With this status, the
	 * component will be {@link #Satisfied} if it is made available.
	 */
	Unavailable,
	/**
	 * All of the component's non-{@link Dependency#isDynamic() dynamic} dependencies have been satisfied by components that are themselves
	 * at least {@link #PreSatisfied pre-satisfied}. Dynamic dependencies are known to be satisfiable and shall be satisfied shortly.
	 *
	 * Components that are pre-satisfied may be used to satisfy dependencies, and must therefore be able to behave and perform its essential
	 * capabilities before it is completely {@link #Satisfied}.
	 */
	PreSatisfied,
	/**
	 * All of the component's dependencies have been satisfied by components that are themselves at least {@link #PreSatisfied
	 * pre-satisfied}.
	 */
	Satisfied,
	/**
	 * The component has been {@link ComponentController#remove() removed}. Removed components cannot be re-added, so this stage is
	 * terminal.
	 */
	Removed;

	/** @return Whether a component at this stage should be active in the dependency service */
	public boolean isActive() {
		switch (this) {
		case PreSatisfied:
		case Satisfied:
			return true;
		default:
			return false;
		}
	}

	/** @return Whether a component at this stage has all its dependencies satisfied */
	public boolean isSatisfied() {
		switch (this) {
		case Unavailable:
		case Satisfied:
			return true;
		default:
			return false;
		}
	}
}
