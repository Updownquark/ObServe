package org.observe.test;

import java.awt.Image;

import org.observe.config.OperationResult;

/** Allows tests to interact with the user */
public interface UserInteraction {
	/**
	 * Asks the user to do something, e.g. press a button
	 *
	 * @param title The title of the dialog
	 * @param message The message in the dialog
	 * @param image The image to display in the dialog
	 * @return A result that the user can use to wait on the user to close the dialog or observe when it is closed
	 * @throws TestCanceledException If the test has been canceled
	 */
	OperationResult<Void> instructUser(String title, String message, Image image) throws TestCanceledException;

	/**
	 * Asks the user a yes/no question
	 *
	 * @param title The title of the dialog
	 * @param question The message in the dialog
	 * @param image The image to display in the dialog
	 * @return A result that the user can use to wait on the user to close the dialog or observe when it is closed
	 * @throws TestCanceledException If the test has been canceled
	 */
	OperationResult<Boolean> confirm(String title, String question, Image image) throws TestCanceledException;

	/**
	 * Closes any user interactions currently active and throws a {@link TestCanceledException} from any current or future user interaction
	 * methods until {@link #reset()} is called
	 */
	void cancel();

	/** Resets after potentially having called {@link #cancel()} */
	void reset();
}
