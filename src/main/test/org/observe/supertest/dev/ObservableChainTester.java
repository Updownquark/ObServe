package org.observe.supertest.dev;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;
import org.qommons.QommonsUtils;
import org.qommons.Ternian;
import org.qommons.TestHelper;
import org.qommons.TestHelper.Testable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.debug.Debug;

import com.google.common.reflect.TypeToken;

public class ObservableChainTester implements Testable {
	public static enum TestValueType {
		INT(TypeToken.of(int.class)), DOUBLE(TypeToken.of(double.class)), STRING(TypeToken.of(String.class));
		// TODO Add an array type for each

		private final TypeToken<?> type;

		private TestValueType(TypeToken<?> type) {
			this.type = type;
		}

		public TypeToken<?> getType() {
			return type;
		}
	}

	public static TestValueType nextType(TestHelper helper) {
		// The DOUBLE type is much less performant. There may be some value, but we'll use it less often.
		ValueHolder<TestValueType> result = new ValueHolder<>();
		TestHelper.RandomAction action = helper.createAction();
		action.or(10, () -> result.accept(TestValueType.INT));
		action.or(5, () -> result.accept(TestValueType.STRING));
		action.or(2, () -> result.accept(TestValueType.DOUBLE));
		action.execute(null);
		return result.get();
		// return TestValueType.values()[helper.getInt(0, TestValueType.values().length)];
	}

	private static final int MAX_VALUE = 1000;
	static final Map<TestValueType, Function<TestHelper, ?>> SUPPLIERS;
	static {
		SUPPLIERS = new HashMap<>();
		for (TestValueType type : TestValueType.values()) {
			switch (type) {
			case INT:
				SUPPLIERS.put(type, helper -> helper.getInt(0, MAX_VALUE));
				break;
			case DOUBLE:
				SUPPLIERS.put(type, helper -> helper.getDouble(0, MAX_VALUE));
				break;
			case STRING:
				SUPPLIERS.put(type, helper -> String.valueOf(helper.getInt(0, MAX_VALUE)));
			}
		}
	}

	private static String stringValueOf(double d) {
		String str = String.valueOf(d);
		if (str.endsWith(".0"))
			str = str.substring(0, str.length() - 2);
		return str;
	}

	private static final int MAX_CHAIN_LENGTH = 15;

	private final List<ObservableChainLink<?>> theChain = new ArrayList<>();

	@Override
	public void accept(TestHelper helper) {
		boolean debugging = helper.isReproducing();
		if (debugging)
			Debug.d().start().watchFor(new Debugging());
		assemble(helper);
		test(helper);
		if (debugging)
			Debug.d().end();
	}

	/**
	 * Generates a set of random test cases to test ObServe functionality. If there are previous known failures, this method will execute
	 * them first. The first failure will be persisted and the test will end.
	 */
	@Test
	public void superTest() {
		Duration testDuration = Duration.ofMinutes(5);
		int maxFailures = 1;
		System.out.println(
			"Executing up to " + QommonsUtils.printTimeLength(testDuration.toMillis()) + " of tests with max " + maxFailures + " failures");
		TestHelper.TestSummary summary = TestHelper.createTester(getClass())//
			/**/.withRandomCases(-1).withMaxTotalDuration(testDuration)//
			/**/.withMaxFailures(maxFailures)//
			/**/.withPersistenceDir(new File("src/main/test/org/observe/supertest/dev"), false)//
			/**/.withPlacemarks("Transaction", "Modification")
			/**/.withDebug(true)//
			/**/.execute();
		System.out.println("Summary: " + summary);
		summary.throwErrorIfFailed();
	}

	private <E> void assemble(TestHelper helper) {
		//Tend toward smaller chain lengths, but allow longer ones occasionally
		int chainLength = helper.getInt(2, helper.getInt(2, MAX_CHAIN_LENGTH));
		ObservableChainLink<?> initLink = SimpleCollectionLink.createInitialLink(null, null, helper, 0, Ternian.NONE, null);
		theChain.add(initLink);
		initLink.initialize(helper);
		while (theChain.size() < chainLength) {
			ObservableChainLink<?> nextLink = theChain.get(theChain.size() - 1).derive(helper);
			theChain.add(nextLink);
			nextLink.initialize(helper);
		}
	}

	private void test(TestHelper helper) {
		System.out.println("Assembled " + printChain());
		if (helper.isReproducing()) {
			System.out.println("Initial Values:");
			System.out.println(this);
		}
		int failedLink = 0;
		try {
			for (failedLink = 0; failedLink < theChain.size(); failedLink++)
				theChain.get(failedLink).check(true);
		} catch (Error e) {
			System.err.println("Integrity check failure on initial values on link " + failedLink);
			throw e;
		}

		int tries = 50;
		long modifications = 0;
		for (int tri = 0; tri < tries; tri++) {
			int linkIndex = helper.getInt(0, theChain.size());
			ObservableChainLink<?> targetLink = theChain.get(linkIndex);
			boolean finished = false;
			boolean useTransaction = helper.getBoolean(.75);
			try (Transaction t = useTransaction ? targetLink.lock() : Transaction.NONE) {
				int transactionMods = (int) helper.getDouble(1, 10, 26);
				if (transactionMods == 25)
					transactionMods = 0; // Want the probability of no-op transactions to be small but present
				if (helper.isReproducing())
					System.out.println("Modification set " + (tri + 1) + ": " + transactionMods + " modifications on link " + linkIndex);
				helper.placemark("Transaction");
				for (int transactionTri = 0; transactionTri < transactionMods; transactionTri++) {
					String preValue = toString();
					if (helper.isReproducing())
						System.out.print("\tMod " + (transactionTri + 1) + ": ");
					try {
						targetLink.tryModify(helper);
					} catch (RuntimeException | Error e) {
						System.err.println("Chain is " + printChain());
						System.err.println("Link " + linkIndex);
						System.err.println("Error on transaction " + (tri + 1) + ", mod " + (transactionTri + 1) + " after "
							+ (modifications + transactionTri) + " successful modifications");
						System.err.println("Pre-failure values:\n" + preValue);
						System.err.println("Post-failure values:\n" + toString());
						throw e;
					}
					try {
						for (failedLink = 0; failedLink < theChain.size(); failedLink++)
							theChain.get(failedLink).check(!useTransaction);
					} catch (Error e) {
						System.err.println("Chain is " + printChain());
						System.err.println("Link " + linkIndex);
						System.err.println("Integrity check failure on link " + failedLink + " after "
							+ (modifications + transactionTri + 1) + " modifications in " + (tri + 1) + " transactions");
						System.err.println("Pre-failure values:\n" + preValue);
						System.err.println("Post-failure values:\n" + toString());
						throw e;
					}
				}
				modifications += transactionMods;
				if (!helper.isReproducing()) {
					if (tri % 10 == 9)
						System.out.print('|');
					else
						System.out.print('.');
					System.out.flush();
				}
				finished = true;
			} catch (RuntimeException | Error e) {
				if (finished) {
					System.err.println("Chain is " + printChain());
					System.err.println("Link " + linkIndex);
					System.err.println("Error closing transaction " + tri + " after " + modifications + " successful modifications");
				}
				throw e;
			}
			if (helper.isReproducing())
				System.out.println("Values:\n" + this);
			try {
				for (failedLink = 0; failedLink < theChain.size(); failedLink++)
					theChain.get(failedLink).check(true);
			} catch (Error e) {
				System.err.println("Chain is " + printChain());
				System.err.println("Link " + linkIndex);
				System.err.println("Integrity check failure after transaction " + (tri + 1) + " close on link " + failedLink + " after "
					+ modifications + " modifications");
				throw e;
			}
		}
	}

	public String printChain() {
		StringBuilder s = new StringBuilder().append(theChain.size()).append(':');
		for (int i = 0; i < theChain.size(); i++) {
			if (i > 0)
				s.append("->");
			s.append('[').append(i).append(']').append(theChain.get(i));
		}
		return s.toString();
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < theChain.size(); i++) {
			if (i > 0)
				s.append('\n');
			s.append('[').append(i).append(']').append(theChain.get(i).printValue());
		}
		return s.toString();
	}
}
