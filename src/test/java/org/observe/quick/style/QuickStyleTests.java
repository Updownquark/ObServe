package org.observe.quick.style;

import org.junit.Test;
import org.observe.ObservableValue;
import org.observe.expresso.AbstractExpressoTest;
import org.observe.expresso.Expresso;
import org.qommons.config.QonfigEvaluationException;

/** Tests the default implementation of the Quick-Style toolkit */
public class QuickStyleTests extends AbstractExpressoTest<Expresso> {
	@Override
	protected String getTestAppFile() {
		return "quick-style-tests-app.qml";
	}

	/**
	 * Tests basic on-element styles. This test tests the {@link ObservableValue#get()} method of getting style information.
	 *
	 * @throws QonfigEvaluationException If an error occurs executing the test
	 */
	@Test
	public void testBasicNoWatch() throws QonfigEvaluationException {
		getTesting().executeTest("basicStyleNoWatch");
	}

	/**
	 * Tests basic on-element styles. This test tests the ability to listen for changes to style information.
	 *
	 * @throws QonfigEvaluationException If an error occurs executing the test
	 */
	@Test
	public void testBasicWithWatch() throws QonfigEvaluationException {
		getTesting().executeTest("basicStyleWithWatch");
	}

	/**
	 * Tests basic inline style sheet functionality
	 *
	 * @throws QonfigEvaluationException If an error occurs executing the test
	 */
	@Test
	public void testLocalStyleSheet() throws QonfigEvaluationException {
		getTesting().executeTest("localStyleSheet");
	}
}
