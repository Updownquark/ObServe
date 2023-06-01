package org.observe;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Function;

import org.junit.Test;
import org.observe.supertest.ObservableChainTester;
import org.observe.supertest.TestValueType;
import org.observe.supertest.collect.BaseCollectionLink;
import org.observe.supertest.collect.FilteredCollectionLink;
import org.qommons.StringUtils;
import org.qommons.testing.TestHelper;

import com.google.common.reflect.TypeToken;

/** Unit tests for {@link ObservableValue} */
public class ObservableValueTest {
	/**
	 * Tests {@link ObservableValue#firstValue(TypeToken, java.util.function.Predicate, java.util.function.Supplier, ObservableValue...)}
	 */
	@Test
	public void testFirstValue() {
		TestHelper.createTester(FirstValueTester.class)//
		.withFailurePersistence(true).revisitKnownFailures(true).withDebug(true)//
		.withPersistenceDir(new File("src/main/test/org/observe"), false)//
		.withMaxTotalDuration(Duration.ofSeconds(2)).withMaxFailures(1)//
		.withMaxProgressInterval(Duration.ofSeconds(1))//
		// .withConcurrency(max -> max - 1)//
		.execute()//
		.throwErrorIfFailed().printResults();
	}

	/**
	 * Testable for
	 * {@link ObservableValue#firstValue(TypeToken, java.util.function.Predicate, java.util.function.Supplier, ObservableValue...)}
	 */
	public static class FirstValueTester implements TestHelper.Testable {
		@Override
		public void accept(TestHelper helper) {
			TestValueType type = BaseCollectionLink.nextType(helper);
			// We want to cover the zero-length test case, but it's trivial, so not very often
			int count = helper.getBoolean(.005) ? 0 : helper.getInt(1, 20);
			SettableValue<Object>[] values = new SettableValue[count];
			for (int i = 0; i < count; i++) {
				values[i] = SettableValue.build((TypeToken<Object>) type.getType())//
					.withValue(ObservableChainTester.SUPPLIERS.get(type).apply(helper)).build();
			}
			Object defValue = helper.getBoolean() ? null : ObservableChainTester.SUPPLIERS.get(type).apply(helper);
			Function<Object, String> test = FilteredCollectionLink.filterFor(type, helper);
			ObservableValue<Object> first = ObservableValue.firstValue((TypeToken<Object>) type.getType(), v -> test.apply(v) == null,
				() -> defValue, values);

			System.out.println("Testing " + count + " " + type + "s with " + test + ", default " + defValue);
			helper.placemark();
			ObservableValueTester<Object> tester = new ObservableValueTester<>(first);
			boolean initial = true;
			int change = 0;
			int target = -1;
			boolean changing = false;
			StringBuilder preValues = new StringBuilder(), postValues = new StringBuilder();
			try {
				StringUtils.print(postValues, ", ", Arrays.asList(values), (s, v) -> {
					if (test.apply(v.get()) == null)
						s.append('*');
					s.append(v.get());
				});
				if (helper.isReproducing())
					System.out.println("Initial values: " + postValues);
				check(tester, values, test, defValue);
				initial = false;
				if (count == 0)
					return;
				for (; change < 100; change++) {
					StringBuilder temp = preValues;
					preValues = postValues;
					postValues = temp;
					postValues.setLength(0);
					target = helper.getInt(0, count);
					changing = true;
					Object newValue = ObservableChainTester.SUPPLIERS.get(type).apply(helper);
					if (helper.isReproducing())
						System.out.println("Change " + change + ": [" + target + "] " + values[target].get() + "->" + newValue);
					changing = false;
					values[target].set(newValue, null);
					StringUtils.print(postValues, ", ", Arrays.asList(values), (s, v) -> {
						if (test.apply(v.get()) == null)
							s.append('*');
						s.append(v.get());
					});
					check(tester, values, test, defValue);
					if (helper.isReproducing())
						System.out.println("Values: " + postValues);
				}
			} catch (RuntimeException | Error e) {
				String msg = "Error ";
				if (initial)
					msg += "on initial values";
				else if (changing)
					msg += " during change " + change + " on value " + target;
				else
					msg += "after change " + change + " on value " + target;
				System.err.println(msg);
				if (initial)
					System.err.println("Values: " + preValues);
				else {
					System.err.println("Pre-change values:  " + preValues);
					if (!changing)
						System.err.println("Post-change values: " + postValues);
				}
				throw e;
			}
		}

		private void check(ObservableValueTester<Object> tester, SettableValue<Object>[] values, Function<Object, String> test,
			Object defValue) {
			for (SettableValue<Object> v : values) {
				if (test.apply(v.get()) == null) {
					tester.check(v.get());
					return;
				}
			}
			tester.check(defValue);
		}
	}
}
