package org.observe.test;

/** A stage of testing */
public enum TestStage {
	/** Setting up the test within the architecture */
	Initialize,
	/** During the {@link InteractiveTest#setup(InteractiveTesting)} method */
	Setup,
	/** During the {@link InteractiveTest#execute(InteractiveTesting)} method */
	Execution,
	/** During the {@link InteractiveTest#analyze(InteractiveTesting)} method */
	Analysis
}