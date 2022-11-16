package org.observe.expresso;

import java.util.List;

import org.junit.Test;

/** Some tests for Expresso functionality */
public class ExpressoTests extends AbstractExpressoTest<Expresso> {
	@Override
	protected String getTestAppFile() {
		return "expresso-tests-app.qml";
	}

	@Override
	public void prepareTest() {
		super.prepareTest();
	}

	/** Tests the constant model type */
	@Test
	public void testConstant() {
		getTesting().executeTest("constant");
	}

	/** Tests the value model type, initialized with or set to a simple value */
	@Test
	public void testSimpleValue() {
		getTesting().executeTest("simpleValue");
	}

	/** Tests the value model type, slaved to an expression */
	@Test
	public void testDerivedValue() {
		getTesting().executeTest("derivedValue");
	}

	/** Tests the list model type */
	@Test
	public void testList() {
		getTesting().executeTest("list");
	}

	/** Tests the mapping transformation type */
	@Test
	public void testMapTo() {
		getTesting().executeTest("mapTo");
	}

	/** Tests the sort transformation type */
	@Test
	public void testSort() {
		getTesting().executeTest("sort");
	}

	/** Tests int assignment */
	@Test
	public void testAssignInt() {
		getTesting().executeTest("assignInt");
	}

	/** Tests instant assignment */
	@Test
	public void testAssignInstant() {
		getTesting().executeTest("assignInstant");
	}

	/** Tests value-derived model values (see {@link DynamicModelValue}) */
	@Test
	public void testStaticInternalState() {
		getTesting().executeTest("staticInternalState");
	}

	/** Tests dynamically-typed value-derived model values (see {@link DynamicModelValue}) */
	@Test
	public void testDynamicTypeInternalState() {
		getTesting().executeTest("dynamicTypeInternalState");
	}

	/** Tests dynamically-typed value-derived model values where the model value is tied to the attribute via API */
	@Test
	public void testDynamicTypeInternalState2() {
		getTesting().executeTest("dynamicTypeInternalState2");
	}

	/**
	 * Called by the sort test from Expresso. Ensures that all the entities are properly sorted.
	 *
	 * @param entities The entities to check
	 * @throws AssertionError If the entities are not in order
	 */
	public static void checkEntityListOrder(List<ExpressoTestEntity> entities) throws AssertionError {
		ExpressoTestEntity prev = null;
		for (ExpressoTestEntity entity : entities) {
			if (prev != null && prev.compareByFields(entity) > 0)
				throw new AssertionError(prev + ">" + entity);
			prev = entity;
		}
	}
}
