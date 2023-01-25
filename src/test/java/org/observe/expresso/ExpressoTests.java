package org.observe.expresso;

import java.util.List;

import org.junit.Test;
import org.qommons.config.QonfigEvaluationException;

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

	/**
	 * Tests the constant model type
	 *
	 * @throws QonfigEvaluationException If an error occurs executing the test
	 */
	@Test
	public void testConstant() throws QonfigEvaluationException {
		getTesting().executeTest("constant");
	}

	/**
	 * Tests the value model type, initialized with or set to a simple value
	 *
	 * @throws QonfigEvaluationException If an error occurs executing the test
	 */
	@Test
	public void testSimpleValue() throws QonfigEvaluationException {
		getTesting().executeTest("simpleValue");
	}

	/**
	 * Tests the value model type, slaved to an expression
	 *
	 * @throws QonfigEvaluationException If an error occurs executing the test
	 */
	@Test
	public void testDerivedValue() throws QonfigEvaluationException {
		getTesting().executeTest("derivedValue");
	}

	/**
	 * Tests the list model type
	 *
	 * @throws QonfigEvaluationException If an error occurs executing the test
	 */
	@Test
	public void testList() throws QonfigEvaluationException {
		getTesting().executeTest("list");
	}

	/**
	 * Tests the mapping transformation type
	 *
	 * @throws QonfigEvaluationException If an error occurs executing the test
	 */
	@Test
	public void testMapTo() throws QonfigEvaluationException {
		getTesting().executeTest("mapTo");
	}

	/**
	 * Tests the sort transformation type
	 *
	 * @throws QonfigEvaluationException If an error occurs executing the test
	 */
	@Test
	public void testSort() throws QonfigEvaluationException {
		getTesting().executeTest("sort");
	}

	/**
	 * Tests int assignment
	 *
	 * @throws QonfigEvaluationException If an error occurs executing the test
	 */
	@Test
	public void testAssignInt() throws QonfigEvaluationException {
		getTesting().executeTest("assignInt");
	}

	/**
	 * Tests instant assignment
	 *
	 * @throws QonfigEvaluationException If an error occurs executing the test
	 */
	@Test
	public void testAssignInstant() throws QonfigEvaluationException {
		getTesting().executeTest("assignInstant");
	}

	/**
	 * Tests value-derived model values (see {@link DynamicModelValue})
	 *
	 * @throws QonfigEvaluationException If an error occurs executing the test
	 */
	@Test
	public void testStaticInternalState() throws QonfigEvaluationException {
		getTesting().executeTest("staticInternalState");
	}

	/**
	 * Tests dynamically-typed value-derived model values (see {@link DynamicModelValue})
	 *
	 * @throws QonfigEvaluationException If an error occurs executing the test
	 */
	@Test
	public void testDynamicTypeInternalState() throws QonfigEvaluationException {
		getTesting().executeTest("dynamicTypeInternalState");
	}

	/**
	 * Tests dynamically-typed value-derived model values where the model value is tied to the attribute via API
	 *
	 * @throws QonfigEvaluationException If an error occurs executing the test
	 */
	@Test
	public void testDynamicTypeInternalState2() throws QonfigEvaluationException {
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
