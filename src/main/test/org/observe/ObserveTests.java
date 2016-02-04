package org.observe;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.observe.assoc.ObservableDataStructTest;
import org.observe.collect.ObservableCollectionsTest;
import org.observe.util.tree.TreeUtilsTest;

/** Runs all unit tests in the ObServe project. */
@RunWith(Suite.class)
@SuiteClasses({ObservableTest.class, ObservableCollectionsTest.class, TreeUtilsTest.class, ObservableDataStructTest.class})
public class ObserveTests {
}
