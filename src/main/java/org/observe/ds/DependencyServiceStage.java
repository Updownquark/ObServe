package org.observe.ds;

/** The initialization stage of a {@link DependencyService} */
public enum DependencyServiceStage {
	/**
	 * The dependency service has not started its initialization. All components in it will have {@link DSComponent#getStage() stage}s of
	 * {@link ComponentStage#Defined} (or {@link ComponentStage#Unsatisfied} if they are {@link DSComponent#isAvailable() unavailable})
	 */
	Uninitialized,
	/**
	 * The dependency service has started initializing components. Components may be transitioning between {@link DSComponent#getStage()
	 * stage}s of {@link ComponentStage#Defined} and {@link ComponentStage#Satisfied} or {@link ComponentStage#Unsatisfied}.
	 */
	Initializing,
	/**
	 * The dependency service has finished initializing all its components. All of them should be {@link ComponentStage#Unsatisfied},
	 * {@link ComponentStage#Satisfied}, or {@link ComponentStage#Unavailable}.
	 */
	Initialized;
}
