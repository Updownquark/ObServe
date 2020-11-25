package org.observe;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.observe.assoc.ObservableAssocTest;
import org.observe.collect.ObservableCollectionsTest;
import org.observe.config.ObservableConfigTest;
import org.observe.util.EntityArgumentsTest;
import org.observe.util.EntityReflectorTest;
import org.observe.util.swing.ObservableSwingUtilsTests;
import org.qommons.QommonsTests;

/** Runs all unit tests in the ObServe project. */
@RunWith(Suite.class)
@SuiteClasses({ //
	QommonsTests.class, //
	ObservableTest.class, //
	ObservableValueTest.class, //
	ObservableCollectionsTest.class, //
	ObservableAssocTest.class, //
	EntityReflectorTest.class, //
	EntityArgumentsTest.class, //
	ObservableConfigTest.class, //
	ObservableSwingUtilsTests.class
})
public class ObserveTests {
}
