package org.observe.expresso;

import org.junit.Before;
import org.qommons.config.QonfigApp;
import org.qommons.config.QonfigDocument;

/**
 * Abstract class for a unit test backed by an expresso Qonfig file
 *
 * @param <H> The sub-type of this testing's head structure
 */
public abstract class AbstractExpressoTest<H extends Expresso> {
	private static ExpressoTesting<?> TESTING;

	/** @return The location to find the app configuration for the test. See expresso-test-app-template.qml. */
	protected abstract String getTestAppFile();

	private ExpressoTesting<H> theTesting;

	/** @return This test's testing structure */
	public ExpressoTesting<H> getTesting() {
		return theTesting;
	}

	/** Parses the test's QML (for the first test only) */
	@Before
	public void prepareTest() {
		if (TESTING == null) {
			System.out.print("Interpreting test files...");
			System.out.flush();
			try {
				QonfigDocument app = QonfigApp.parseApp(getClass().getResource(getTestAppFile()));
				TESTING = QonfigApp.interpretApp(app, ExpressoTesting.class);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			System.out.println("done");
		}
		theTesting = (ExpressoTesting<H>) TESTING;
	}
}
