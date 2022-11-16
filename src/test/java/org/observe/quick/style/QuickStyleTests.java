package org.observe.quick.style;

import org.junit.Test;
import org.observe.ObservableValue;
import org.observe.expresso.AbstractExpressoTest;
import org.observe.expresso.Expresso;

/** Tests the default implementation of the Quick-Style toolkit */
public class QuickStyleTests extends AbstractExpressoTest<Expresso> {
	@Override
	protected String getTestAppFile() {
		return "quick-style-tests-app.qml";
	}

	/** Tests basic on-element styles. This test tests the {@link ObservableValue#get()} method of getting style information. */
	@Test
	public void testBasicNoWatch() {
		getTesting().executeTest("basicStyleNoWatch");
	}

	/** Tests basic on-element styles. This test tests the ability to listen for changes to style information. */
	@Test
	public void testBasicWithWatch() {
		getTesting().executeTest("basicStyleWithWatch");
	}

	/** Tests basic inline style sheet functionality */
	@Test
	public void testLocalStyleSheet() {
		getTesting().executeTest("localStyleSheet");
	}
}
