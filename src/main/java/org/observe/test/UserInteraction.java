package org.observe.test;

import java.awt.Component;
import java.awt.Image;

import org.observe.config.OperationResult;

/** Allows tests to interact with the user */
public interface UserInteraction {
	/**
	 * Asks the user to wait on the test
	 *
	 * @param title The title of the dialog
	 * @param message The message in the dialog
	 * @param images The images to display in the dialog
	 * @return A result that the code can use to close the dialog
	 * @throws TestCanceledException If the test has been canceled
	 */
	default OperationResult<Void> userWait(String title, String message, Image... images) throws TestCanceledException {
		return userWait(title, message, null, images);
	}

	/**
	 * Asks the user to wait on the test
	 *
	 * @param title The title of the dialog
	 * @param message The message in the dialog
	 * @param parent The parent component to display the dialog on top of
	 * @param images The images to display in the dialog
	 * @return A result that the code can use to close the dialog
	 * @throws TestCanceledException If the test has been canceled
	 */
	OperationResult<Void> userWait(String title, String message, Component parent, Image... images) throws TestCanceledException;

	/**
	 * Asks the user to do something, e.g. press a button
	 *
	 * @param title The title of the dialog
	 * @param message The message in the dialog
	 * @param images The images to display in the dialog
	 * @return A result that the user can use to wait on the user to close the dialog, observe when it is closed, or close it itself
	 * @throws TestCanceledException If the test has been canceled
	 */
	default OperationResult<Void> instructUser(String title, String message, Image... images) throws TestCanceledException {
		return instructUser(title, message, null, images);
	}

	/**
	 * Asks the user to do something, e.g. press a button
	 *
	 * @param title The title of the dialog
	 * @param message The message in the dialog
	 * @param parent The parent component to display the dialog on top of
	 * @param images The images to display in the dialog
	 * @return A result that the user can use to wait on the user to close the dialog, observe when it is closed, or close it itself
	 * @throws TestCanceledException If the test has been canceled
	 */
	OperationResult<Void> instructUser(String title, String message, Component parent, Image... images) throws TestCanceledException;

	/**
	 * Asks the user a yes/no question
	 *
	 * @param title The title of the dialog
	 * @param question The message in the dialog
	 * @param images The images to display in the dialog
	 * @return A result that the user can use to wait on the user to close the dialog, observe when it is closed (and the user's response),
	 *         or close it itself
	 * @throws TestCanceledException If the test has been canceled
	 */
	default OperationResult<Boolean> confirm(String title, String question, Image... images) throws TestCanceledException {
		return confirm(title, question, null, images);
	}

	/**
	 * Asks the user a yes/no question
	 *
	 * @param title The title of the dialog
	 * @param question The message in the dialog
	 * @param parent The parent component to display the dialog on top of
	 * @param images The images to display in the dialog
	 * @return A result that the user can use to wait on the user to close the dialog, observe when it is closed (and the user's response),
	 *         or close it itself
	 * @throws TestCanceledException If the test has been canceled
	 */
	OperationResult<Boolean> confirm(String title, String question, Component parent, Image... images) throws TestCanceledException;

	/**
	 * Closes any user interactions currently active and throws a {@link TestCanceledException} from any current or future user interaction
	 * methods until {@link #reset()} is called
	 */
	void cancel();

	/** Resets after potentially having called {@link #cancel()} */
	void reset();
}
