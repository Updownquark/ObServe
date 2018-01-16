package org.observe.supertest.dev;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;
import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.FlowOptions;
import org.qommons.QommonsUtils;
import org.qommons.TestHelper;
import org.qommons.TestHelper.Testable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;

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
		return TestValueType.values()[helper.getInt(0, TestValueType.values().length)];
	}

	private static final int MAX_VALUE = 1000;
	static final Map<TestValueType, Function<TestHelper, ?>> SUPPLIERS;
	private static final Map<TestValueType, List<? extends Comparator<?>>> COMPARATORS;
	static {
		SUPPLIERS = new HashMap<>();
		COMPARATORS = new HashMap<>();
		for (TestValueType type : TestValueType.values()) {
			switch (type) {
			case INT:
				SUPPLIERS.put(type, helper -> helper.getInt(0, MAX_VALUE));
				COMPARATORS.put(type, Arrays.asList(Integer::compareTo, ((Comparator<Integer>) Integer::compareTo).reversed()));
				break;
			case DOUBLE:
				SUPPLIERS.put(type, helper -> helper.getDouble(0, MAX_VALUE));
				COMPARATORS.put(type, Arrays.asList(Double::compareTo, ((Comparator<Double>) Double::compareTo).reversed()));
				break;
			case STRING:
				SUPPLIERS.put(type, helper -> String.valueOf(helper.getInt(0, MAX_VALUE)));
				COMPARATORS.put(type, Arrays.asList(String::compareTo, ((Comparator<String>) String::compareTo).reversed()));
			}
		}
	}

	private static String stringValueOf(double d) {
		String str = String.valueOf(d);
		if (str.endsWith(".0"))
			str = str.substring(0, str.length() - 2);
		return str;
	}

	private static <E> Comparator<E> randomComparator(TestValueType type, TestHelper helper) {
		List<Comparator<E>> typeCompares = (List<Comparator<E>>) COMPARATORS.get(type);
		return typeCompares.get(helper.getInt(0, typeCompares.size()));
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
		ObservableChainLink<?> initLink = createInitialLink(helper);
		theChain.add(initLink);
		while (theChain.size() < chainLength)
			theChain.add(theChain.get(theChain.size() - 1).derive(helper));
	}

	private <E> ObservableChainLink<?> createInitialLink(TestHelper helper) {
		int linkTypes = 2;
		switch (helper.getInt(0, linkTypes)) {
		case 0:
			// TODO Uncomment this when CircularArrayList is working
			// return new ObservableCollectionLinkTester<>(null, new DefaultObservableCollection<>((TypeToken<E>) type.type,
			// CircularArrayList.build().build()));
		case 1:
			TestValueType type = TestValueType.values()[helper.getInt(0, TestValueType.values().length)];
			BetterList<E> backing = new BetterTreeList<>(true);
			DefaultObservableCollection<E> base = new DefaultObservableCollection<>((TypeToken<E>) type.getType(), backing);
			return new SimpleCollectionLink<>(type, base.flow(), helper);
		case 2:
			type = TestValueType.values()[helper.getInt(0, TestValueType.values().length)];
			Comparator<? super E> compare = randomComparator(type, helper);
			backing = new BetterTreeSet<>(false, compare);
			base = new DefaultObservableCollection<>((TypeToken<E>) type.getType(), backing);
			SimpleCollectionLink<E> simple = new SimpleCollectionLink<>(type, base.flow(), helper);
			return new SortedDistinctCollectionLink<>(simple, type, base.flow(), helper, compare,
				new FlowOptions.GroupingDef(new FlowOptions.GroupingOptions(true)));
			// TODO ObservableValue
			// TODO ObservableMultiMap
			// TODO ObservableMap
			// TODO ObservableTree?
			// TODO ObservableGraph?
		}
		throw new IllegalStateException();
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
				else {
					System.out.print('.');
					System.out.flush();
				}
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
						System.err.println("Pre-faiure values:\n" + preValue);
						System.err.println("Post-faiure values:\n" + toString());
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
						System.err.println("Pre-faiure values:\n" + preValue);
						System.err.println("Post-faiure values:\n" + toString());
						throw e;
					}
				}
				modifications += transactionMods;
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
				System.err.println(
					"Integrity check failure on transaction close on link " + failedLink + " after " + modifications + " modifications");
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
