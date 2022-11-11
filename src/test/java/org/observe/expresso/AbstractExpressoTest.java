package org.observe.expresso;

import org.junit.Before;
import org.qommons.config.QonfigApp;

public abstract class AbstractExpressoTest<H extends Expresso> {
	private static ExpressoTesting<?> TESTING;

	protected abstract String getTestAppFile();

	private ExpressoTesting<H> theTesting;

	public ExpressoTesting<H> getTesting() {
		return theTesting;
	}

	@Before
	public void prepareTest() {
		if (TESTING == null) {
			System.out.print("Interpreting test files...");
			System.out.flush();
			try {
				TESTING = QonfigApp.interpretApp(getClass().getResource(getTestAppFile()), ExpressoTesting.class);
			} catch (RuntimeException e) {
				e.printStackTrace();
				throw e;
			}
			System.out.println("done");
		}
		theTesting = (ExpressoTesting<H>) TESTING;
	}
}
