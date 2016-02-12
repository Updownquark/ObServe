package org.observe;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.observe.assoc.ObservableAssocTest;
import org.observe.collect.ObservableCollectionsTest;
import org.qommons.tree.TreeUtilsTest;

/** Runs all unit tests in the ObServe project. */
@RunWith(Suite.class)
@SuiteClasses({ObservableTest.class, ObservableCollectionsTest.class, TreeUtilsTest.class, ObservableAssocTest.class})
public class ObserveTests {
}
