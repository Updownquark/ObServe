package org.observe.quick.style;

import org.junit.Test;
import org.observe.expresso.AbstractExpressoTest;
import org.observe.expresso.Expresso;

public class QuickStyleTests extends AbstractExpressoTest<Expresso> {
	@Override
	protected String getTestAppFile() {
		return "quick-style-tests-app.qml";
	}

	@Test
	public void test0() {
		getTesting().executeTest("test0");
	}
}
