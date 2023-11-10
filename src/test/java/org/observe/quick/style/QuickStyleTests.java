package org.observe.quick.style;

import org.junit.Test;
import org.observe.ObservableValue;
import org.observe.expresso.AbstractExpressoTest;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExpressoHeadSection;

/** Tests the default implementation of the Quick-Style toolkit */
public class QuickStyleTests extends AbstractExpressoTest<ExpressoHeadSection> {
	@Override
	protected String getTestAppFile() {
		return "quick-style-tests-app.qml";
	}

	/**
	 * Tests basic on-element styles. This test tests the {@link ObservableValue#get()} method of getting style information.
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test structures
	 */
	@Test
	public void testBasicNoWatch() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("basicStyleNoWatch");
	}

	/**
	 * Tests basic on-element styles. This test tests the ability to listen for changes to style information.
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test structures
	 */
	@Test
	public void testBasicWithWatch() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("basicStyleWithWatch");
	}

	/**
	 * Tests basic inline style sheet functionality
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test structures
	 */
	@Test
	public void testLocalStyleSheet() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("localStyleSheet");
	}

	/**
	 * Tests against an inline style sheet with conditionally-applied style sets
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test structures
	 */
	@Test
	public void testWithStyleSets() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("withStyleSets");
	}
}
