package org.observe.quick;

import org.junit.Test;
import org.observe.expresso.AbstractExpressoTest;
import org.observe.expresso.Expresso;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;

/** Tests the default implementation of the Quick toolkit */
public class QuickTests extends AbstractExpressoTest<Expresso> {
	@Override
	protected String getTestAppFile() {
		return "quick-tests-app.qml";
	}

	/**
	 * The super-basic test
	 *
	 * @throws ExpressoInterpretationException If an exception occurs interpreting the test
	 * @throws ModelInstantiationException If an exception occurs instantiating the test
	 */
	@Test
	public void testSuperBasic() throws ExpressoInterpretationException, ModelInstantiationException {
		getTesting().executeTest("superBasic");
	}

	/**
	 * Test with internal model values
	 *
	 * @throws ExpressoInterpretationException If an exception occurs interpreting the test
	 * @throws ModelInstantiationException If an exception occurs instantiating the test
	 */
	@Test
	public void testWithElementModels() throws ExpressoInterpretationException, ModelInstantiationException {
		getTesting().executeTest("withElementModels");
	}
}
