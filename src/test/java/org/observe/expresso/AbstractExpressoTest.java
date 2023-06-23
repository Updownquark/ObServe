package org.observe.expresso;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Before;
import org.qommons.config.QonfigApp;

/**
 * Abstract class for a unit test backed by an expresso Qonfig file
 *
 * @param <H> The sub-type of this testing's head structure
 */
public abstract class AbstractExpressoTest<H extends Expresso> {
	private static final Map<Class<?>, ExpressoTesting<?>> TESTING = new ConcurrentHashMap<>();

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
		theTesting = (ExpressoTesting<H>) TESTING.computeIfAbsent(getClass(), __ -> {
			System.out.print("Interpreting test files...");
			System.out.flush();
			ExpressoTesting<?> testing;
			try {
				QonfigApp app = QonfigApp.parseApp(getClass().getResource(getTestAppFile()));
				testing = app.interpretApp(ExpressoTesting.class);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			System.out.println("done");
			return testing;
		});
	}
}
