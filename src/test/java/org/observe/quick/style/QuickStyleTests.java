package org.observe.quick.style;

import org.junit.Test;
import org.observe.expresso.AbstractExpressoTest;
import org.observe.expresso.Expresso;

/** Tests the default implementation of the Quick-Style toolkit */
public class QuickStyleTests extends AbstractExpressoTest<Expresso> {
	@Override
	protected String getTestAppFile() {
		return "quick-style-tests-app.qml";
	}

	/** Tests basic on-element styles */
	@Test
	public void testBasic() {
		getTesting().executeTest("basicStyle");
	}
}
