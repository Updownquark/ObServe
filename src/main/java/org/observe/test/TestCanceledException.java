package org.observe.test;

import org.observe.config.ValueOperationException;

/** Thrown by some methods in the architecture after a test has been {@link InteractiveTestingService#cancelTest() canceled} */
public class TestCanceledException extends ValueOperationException {
	/** Creates the exception */
	public TestCanceledException() {
		super("Test Canceled");
	}
}
