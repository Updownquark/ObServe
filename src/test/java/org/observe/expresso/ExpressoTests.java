package org.observe.expresso;

import java.util.List;
import java.util.function.Supplier;

import org.junit.Test;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.expresso.qonfig.ElementModelValue;
import org.observe.expresso.qonfig.ExpressoHeadSection;
import org.observe.util.TypeTokens;
import org.qommons.testing.TestUtil;

/** Some tests for Expresso functionality */
public class ExpressoTests extends AbstractExpressoTest<ExpressoHeadSection> {
	@Override
	protected String getTestAppFile() {
		return "expresso-tests-app.qml";
	}

	@Override
	public void compileTesting() {
		super.compileTesting();
	}

	/**
	 * Tests the constant model type
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testConstant() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("constant");
	}

	/**
	 * Tests the value model type, initialized with or set to a simple value
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testSimpleValue() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("simpleValue");
	}

	/**
	 * Tests the value model type, slaved to an expression
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testDerivedValue() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("derivedValue");
	}

	/**
	 * Tests the list model type
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testList() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("list");
	}

	/**
	 * Tests unary operators
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testUnaryOperators() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("unaryOperators");
	}

	/**
	 * Tests boolean operators
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testBooleanOperators() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("booleanOperators");
	}

	/**
	 * Tests comparison operators
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testComparisonOperators() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("comparisonOperators");
	}

	/**
	 * Tests math operators
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testMathOperators() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("mathOperators");
	}

	/**
	 * Tests cast operators
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testCasts() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("casts");
	}

	/**
	 * Tests the string concatenation operator (+)
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testStringConcat() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("stringConcat");
	}

	/**
	 * Tests bitwise operators
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testBitwiseOperators() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("bitwiseOperators");
	}

	/**
	 * Tests the Object OR operation
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testObjectOr() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("objectOr");
	}

	/**
	 * Tests the conditional operator (condition ? primary : secondary)
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testConditionalOperator() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("conditionalOperator");
	}

	/**
	 * Tests the mapping transformation type
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testMapTo() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("mapTo");
	}

	/**
	 * Tests if/else and switch/case structures in expresso
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testIfElseSwitchCase() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("ifElseSwitchCase");
	}

	/**
	 * Tests the sort transformation type
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testSort() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("sort");
	}

	/**
	 * Tests int assignment
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testAssignInt() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("assignInt");
	}

	/**
	 * Tests instant assignment
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testAssignInstant() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("assignInstant");
	}

	/**
	 * Tests reflection operations
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testReflection() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("reflection");
	}

	/**
	 * Tests for class instances (e.g. 'int.class') and instanceof
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testClasses() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("classes");
	}

	/**
	 * Tests instant assignment
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testHook() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("hook");
	}

	/**
	 * Tests value-derived model values (see {@link ElementModelValue})
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testStaticInternalState() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("staticInternalState");
	}

	/**
	 * Tests dynamically-typed value-derived model values (see {@link ElementModelValue})
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testDynamicTypeInternalState() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("dynamicTypeInternalState");
	}

	/**
	 * Tests dynamically-typed value-derived model values where the model value is tied to the attribute via API
	 *
	 * @throws ExpressoInterpretationException If an error occurs interpreting the test
	 * @throws ModelInstantiationException If an error occurs instantiating the test values
	 */
	@Test
	public void testDynamicTypeInternalState2() throws ExpressoInterpretationException, ModelInstantiationException {
		executeTest("dynamicTypeInternalState2");
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

	/**
	 * A method called from some of the tests
	 *
	 * @param <C> The type to compare
	 * @param type The type to compare
	 * @param a The first value to compare
	 * @param b The second value to compare
	 * @param lt Whether a should be &lt; b
	 * @param lte Whether a should be &lt;= b
	 * @param gt Whether a should be &gt; b
	 * @param gte Whether a should be &gt;= b
	 * @param eq Whether a should be == b
	 * @param neq Whether a should be != b
	 */
	public static <C extends Comparable<C>> void testComparison(Class<C> type, SettableValue<C> a, SettableValue<C> b, //
		SettableValue<Boolean> lt, SettableValue<Boolean> lte, SettableValue<Boolean> gt, SettableValue<Boolean> gte,
		SettableValue<Boolean> eq, SettableValue<Boolean> neq) {
		TestUtil helper = new TestUtil(123456789, 0);
		Supplier<C> random = getRandom(type, helper);
		checkComparison(a, b, lt, lte, gt, gte, eq, neq);

		// Do some random operations with nobody listening to the values
		for (int i = 0; i < 1000; i++) {
			C newValue = random.get();
			(i % 2 == 0 ? a : b).set(newValue, null);
			checkComparison(a, b, lt, lte, gt, gte, eq, neq);
		}

		// Now listen to all the comparables and make sure they report the right changes
		SettableValue<Boolean> ltCopy = SettableValue.<Boolean> build().withValue(false).build();
		SettableValue<Boolean> lteCopy = SettableValue.<Boolean> build().withValue(false).build();
		SettableValue<Boolean> gtCopy = SettableValue.<Boolean> build().withValue(false).build();
		SettableValue<Boolean> gteCopy = SettableValue.<Boolean> build().withValue(false).build();
		SettableValue<Boolean> eqCopy = eq == null ? null : SettableValue.<Boolean> build().withValue(false).build();
		SettableValue<Boolean> neqCopy = neq == null ? null : SettableValue.<Boolean> build().withValue(false).build();
		SimpleObservable<Void> until = new SimpleObservable<>();
		lt.changes().act(evt -> ltCopy.set(evt.getNewValue(), evt));
		lte.changes().act(evt -> lteCopy.set(evt.getNewValue(), evt));
		gt.changes().act(evt -> gtCopy.set(evt.getNewValue(), evt));
		gte.changes().act(evt -> gteCopy.set(evt.getNewValue(), evt));
		if (eq != null)
			eq.changes().act(evt -> eqCopy.set(evt.getNewValue(), evt));
		if (neq != null)
			neq.changes().act(evt -> neqCopy.set(evt.getNewValue(), evt));

		checkComparison(a, b, ltCopy, lteCopy, gtCopy, gteCopy, eqCopy, neqCopy);
		for (int i = 0; i < 1000; i++) {
			C newValue = random.get();
			(i % 2 == 0 ? a : b).set(newValue, null);
			checkComparison(a, b, ltCopy, lteCopy, gtCopy, gteCopy, eqCopy, neqCopy);
		}

		until.onNext(null);
	}

	private static <C extends Comparable<C>> Supplier<C> getRandom(Class<C> type, TestUtil helper) {
		type = TypeTokens.get().unwrap(type);
		if (type == byte.class) {
			return () -> (C) Byte.valueOf(helper.getBytes(1)[0]);
		} else if (type == short.class) {
			return () -> (C) Short.valueOf((short) helper.getAnyInt());
		} else if (type == int.class) {
			return () -> (C) Integer.valueOf(helper.getAnyInt());
		} else if (type == long.class) {
			return () -> (C) Long.valueOf(helper.getAnyLong());
		} else if (type == float.class) {
			// Would be nice to test NaNs as well, but with this way of just representing them as comparables it's pretty difficult
			return () -> {
				float r = helper.getAnyFloat();
				while (Float.isNaN(r))
					r = helper.getAnyFloat();
				return (C) Float.valueOf(r);
			};
		} else if (type == double.class) {
			return () -> {
				double r = helper.getAnyDouble();
				while (Double.isNaN(r))
					r = helper.getAnyDouble();
				return (C) Double.valueOf(r);
			};
		} else if (type == char.class) {
			return () -> (C) Character.valueOf((char) helper.getAnyInt());
		} else if (type == String.class) {
			return () -> (C) helper.getAlphaNumericString(4, 6);
		} else
			throw new IllegalArgumentException("Unrecognized comparable type: " + type.getName());
	}

	private static <C extends Comparable<C>> void checkComparison(SettableValue<C> a, SettableValue<C> b, SettableValue<Boolean> lt,
		SettableValue<Boolean> lte, SettableValue<Boolean> gt, SettableValue<Boolean> gte, SettableValue<Boolean> eq,
		SettableValue<Boolean> neq) {
		int comp = a.get().compareTo(b.get());
		if ((comp < 0) != lt.get())
			throw new AssertionError(a.get() + "<" + b.get() + " reported as " + lt.get());
		if ((comp <= 0) != lte.get())
			throw new AssertionError(a.get() + "<=" + b.get() + " reported as " + lte.get());
		if ((comp > 0) != gt.get())
			throw new AssertionError(a.get() + ">" + b.get() + " reported as " + gt.get());
		if ((comp >= 0) != gte.get())
			throw new AssertionError(a.get() + ">=" + b.get() + " reported as " + gte.get());
		if (eq != null && (comp == 0) != eq.get())
			throw new AssertionError(a.get() + "==" + b.get() + " reported as " + eq.get());
		if (neq != null && (comp != 0) != neq.get())
			throw new AssertionError(a.get() + "!=" + b.get() + " reported as " + neq.get());
	}
}
