package org.observe;

import java.io.File;
import java.time.Duration;
import java.util.function.Function;

import org.junit.Test;
import org.observe.supertest.ObservableChainTester;
import org.observe.supertest.TestValueType;
import org.observe.supertest.links.BaseCollectionLink;
import org.observe.supertest.links.FilteredCollectionLink;
import org.qommons.TestHelper;

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
				values[i] = SettableValue.build((TypeToken<Object>) type.getType()).safe(false)//
					.withValue(ObservableChainTester.SUPPLIERS.get(type).apply(helper)).build();
			}
			Object defValue = helper.getBoolean() ? null : ObservableChainTester.SUPPLIERS.get(type).apply(helper);
			Function<Object, String> test = FilteredCollectionLink.filterFor(type, helper);
			ObservableValue<Object> first = ObservableValue.firstValue((TypeToken<Object>) type.getType(), v -> test.apply(v) == null,
				() -> defValue, values);

			System.out.println("Testing " + count + " " + type + "s with " + test + ", default " + defValue);
			helper.placemark();
			ObservableValueTester<Object> tester = new ObservableValueTester<>(first);
			check(tester, values, test, defValue);
			if (count == 0)
				return;
			for (int change = 0; change < 100; change++) {
				int index = helper.getInt(0, count);
				Object newValue = ObservableChainTester.SUPPLIERS.get(type).apply(helper);
				values[index].set(newValue, null);
				check(tester, values, test, defValue);
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
