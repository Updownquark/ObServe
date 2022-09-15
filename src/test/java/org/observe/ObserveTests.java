package org.observe;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.observe.assoc.ObservableAssocTest;
import org.observe.collect.ObservableCollectionsTest;
import org.observe.config.ObservableConfigTest;
import org.observe.ds.DSTesting;
import org.observe.expresso.ExpressoTests;
import org.observe.remote.ByteAddressTest;
import org.observe.util.CsvEntitySetTest;
import org.observe.util.EntityArgumentsTest;
import org.observe.util.EntityReflectorTest;
import org.observe.util.TypeTokensTest;
import org.observe.util.swing.ObservableSwingUtilsTests;
import org.qommons.QommonsTests;

/** Runs all unit tests in the ObServe project. */
@RunWith(Suite.class)
@SuiteClasses({ //
	ObservableTest.class, //
	ObservableValueTest.class, //
	ObservableCollectionsTest.class, //
	ObservableAssocTest.class, //
	EntityReflectorTest.class, //
	EntityArgumentsTest.class, //
	ObservableConfigTest.class, //
	ObservableSwingUtilsTests.class, //
	DSTesting.class, //
	ByteAddressTest.class, //
	TypeTokensTest.class, //
	CsvEntitySetTest.class, //
	ExpressoTests.class, //
	QommonsTests.class
})
public class ObserveTests {
}
