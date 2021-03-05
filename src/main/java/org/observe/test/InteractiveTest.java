package org.observe.test;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;

import org.qommons.QommonsUtils;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.ex.ExRunnable;
import org.qommons.ex.ExSupplier;

/** A test that may be executed in an {@link InteractiveTestingService} */
public interface InteractiveTest extends InteractiveTestOrSuite {
	/** @return The current human-readable status of the test */
	String getStatusMessage();
	/** @return The estimated length of the test in any unit consistent with {@link #getEstimatedProgress()} */
	double getEstimatedLength();
	/** @return The estimatd amount of the test that has been completed, in any unit consistent with {@link #getEstimatedLength()} */
	double getEstimatedProgress();

	/**
	 * Called first during the test. Intended for setting up of the test, including securing test resources. In reality, there's nothing
	 * preventing any of the 3 testing methods from being used for any purpose.
	 *
	 * @param testing Interface into the testing
	 * @throws Exception If an unexpected problem occurs
	 */
	void setup(InteractiveTesting testing) throws Exception;
	/**
	 * Called second during the test. Intended for execution of the test exclusive without analyzing the results. In reality, there's
	 * nothing preventing any of the 3 testing methods from being used for any purpose.
	 *
	 * @param testing Interface into the testing
	 * @throws Exception If an unexpected problem occurs
	 */
	void execute(InteractiveTesting testing) throws Exception;
	/**
	 * Called last during the test. Intended for analysis of results generated during {@link #execute(InteractiveTesting)}. In reality,
	 * there's nothing preventing any of the 3 testing methods from being used for any purpose.
	 *
	 * @param testing Interface into the testing
	 * @throws Exception If an unexpected problem occurs
	 */
	void analyze(InteractiveTesting testing) throws Exception;

	/**
	 * Called after the test, whether failed or complete
	 *
	 * @throws Exception If an unexpected problem occurs
	 */
	void close() throws Exception;

	/**
	 * Executes a task on the event queue, waiting for it to finish and returning the result or throwing the exception if it occurs
	 *
	 * @param <T> The type of value to return
	 * @param task The task to generate the value
	 * @return The value returned by the task
	 * @throws Exception If the task throws any exception
	 */
	default <T> T onEQ(ExSupplier<T, ?> task) throws Exception {
		Object[] value = new Object[1];
		try {
			EventQueue.invokeAndWait(() -> {
				value[0] = task.unsafe().get();
			});
		} catch (InvocationTargetException e) {
			Throwable toThrow = e.getTargetException();
			if (toThrow instanceof CheckedExceptionWrapper)
				toThrow = ((CheckedExceptionWrapper) toThrow).getCause();
			StackTraceElement[] stack = Thread.currentThread().getStackTrace();
			toThrow.setStackTrace(//
				QommonsUtils.patchStackTraces(e.getStackTrace(), stack, InteractiveTesting.class.getName(), "onEQ"));
			if (toThrow instanceof Exception)
				throw (Exception) toThrow;
			else if (toThrow instanceof Error)
				throw (Error) toThrow;
			else
				throw new CheckedExceptionWrapper(toThrow);
		}
		return (T) value[0];
	}

	/**
	 * Executes a task on the event queue, waiting for it to finish and throwing the exception if it occurs
	 *
	 * @param task The task to generate the value
	 * @throws Exception If the task throws any exception
	 */
	default <T> void onEQ(ExRunnable<?> task) throws Exception {
		onEQ(() -> {
			task.run();
			return null;
		});
	}
}
