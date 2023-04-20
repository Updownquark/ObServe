package org.observe.quick;

import org.junit.Test;
import org.observe.expresso.AbstractExpressoTest;
import org.observe.expresso.Expresso;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;

/** Tests the default implementation of the Quick-Style toolkit */
public class QuickTests2 extends AbstractExpressoTest<Expresso> {
	@Override
	protected String getTestAppFile() {
		return "quick-tests-app.qml";
	}

	@Test
	public void testSuperBasic() throws ExpressoInterpretationException, ModelInstantiationException {
		getTesting().executeTest("superBasic");
	}
}
