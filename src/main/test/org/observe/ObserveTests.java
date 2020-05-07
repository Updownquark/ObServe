package org.observe;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.observe.assoc.ObservableAssocTest;
import org.observe.collect.ObservableCollectionsTest;
import org.qommons.QommonsTests;

/** Runs all unit tests in the ObServe project. */
@RunWith(Suite.class)
@SuiteClasses({ //
	QommonsTests.class, //
	ObservableTest.class, //
	ObservableValueTest.class, //
	ObservableCollectionsTest.class, //
	ObservableAssocTest.class//
})
public class ObserveTests {
}
