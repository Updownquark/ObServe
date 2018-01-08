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
import java.util.stream.Collectors;

import org.junit.Test;
import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.FlowOptions;
import org.qommons.BiTuple;
import org.qommons.QommonsUtils;
import org.qommons.TestHelper;
import org.qommons.TestHelper.Testable;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.debug.Debug;
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

	public interface TypeTransformation<E, T> {
		T map(E source);

		E reverse(T mapped);

		boolean isManyToOne();

		boolean isOneToMany();

		String reverseName();

		default TypeTransformation<T, E> reverse() {
			TypeTransformation<E, T> outer = this;
			return new TypeTransformation<T, E>() {
				@Override
				public E map(T source) {
					return outer.reverse(source);
				}

				@Override
				public T reverse(E mapped) {
					return outer.map(mapped);
				}

				@Override
				public boolean isManyToOne() {
					return outer.isOneToMany();
				}

				@Override
				public boolean isOneToMany() {
					return outer.isManyToOne();
				}

				@Override
				public TypeTransformation<E, T> reverse() {
					return outer;
				}

				@Override
				public String reverseName() {
					return outer.toString();
				}

				@Override
				public String toString() {
					return outer.reverseName();
				}
			};
		}
	}

	public static TestValueType nextType(TestHelper helper) {
		return TestValueType.values()[helper.getInt(0, TestValueType.values().length)];
	}

	public static <E, T> TypeTransformation<E, T> transform(TestValueType type1, TestValueType type2, TestHelper helper) {
		List<? extends TypeTransformation<?, ?>> transforms = TYPE_TRANSFORMATIONS.get(new BiTuple<>(type1, type2));
		return (TypeTransformation<E, T>) transforms.get(helper.getInt(0, transforms.size()));
	}

	private static <E> TypeTransformation<E, E> identity() {
		return new TypeTransformation<E, E>() {
			@Override
			public E map(E source) {
				return source;
			}

			@Override
			public E reverse(E mapped) {
				return mapped;
			}

			@Override
			public boolean isManyToOne() {
				return false;
			}

			@Override
			public boolean isOneToMany() {
				return false;
			}

			@Override
			public String reverseName() {
				return "identity";
			}

			@Override
			public String toString() {
				return "identity";
			}
		};
	}

	private static <E, T> TypeTransformation<E, T> transform(Function<E, T> map, Function<T, E> reverse, boolean manyToOne,
		boolean oneToMany, String name, String reverseName) {
		return new TypeTransformation<E, T>() {
			@Override
			public T map(E source) {
				return map.apply(source);
			}

			@Override
			public E reverse(T mapped) {
				return reverse.apply(mapped);
			}

			@Override
			public boolean isManyToOne() {
				return manyToOne;
			}

			@Override
			public boolean isOneToMany() {
				return oneToMany;
			}

			@Override
			public String reverseName() {
				return reverseName;
			}

			@Override
			public String toString() {
				return name;
			}
		};
	}

	private static <E, T> List<TypeTransformation<E, T>> asList(TypeTransformation<E, T>... transforms) {
		return Arrays.asList(transforms);
	}

	private static String reverse(String s) {
		char[] c = s.toCharArray();
		int start = 0;
		if (c.length > 0 && c[start] == '-')
			start++;
		for (int i = start; i <= c.length / 2; i++) {
			char temp = c[i];
			int opposite = c.length - i - 1;
			c[i] = c[opposite];
			c[opposite] = temp;
		}
		return new String(c);
	}
	private static final int MAX_VALUE = 1000;
	static final Map<TestValueType, Function<TestHelper, ?>> SUPPLIERS;
	private static final Map<TestValueType, List<? extends Comparator<?>>> COMPARATORS;
	private static final Map<BiTuple<TestValueType, TestValueType>, List<? extends TypeTransformation<?, ?>>> TYPE_TRANSFORMATIONS;
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

		TYPE_TRANSFORMATIONS = new HashMap<>();
		for (TestValueType type1 : TestValueType.values()) {
			for (TestValueType type2 : TestValueType.values()) {
				switch (type1) {
				case INT:
					switch (type2) {
					case INT: {
						List<TypeTransformation<Integer, Integer>> transforms = asList(//
							identity(), //
							transform(i -> i + 5, i -> i - 5, false, false, "+5", "-5"), //
							transform(i -> i - 5, i -> i + 5, false, false, "-5", "+5"), //
							transform(i -> -i, i -> -i, false, false, "-", "-"));
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type1, type2), transforms);
						break;
					}
					case DOUBLE: {
						List<TypeTransformation<Integer, Double>> transforms = asList(//
							transform(//
								i -> i * 1.0, //
								d -> (int) Math.round(d), //
								false, true, "*1.0", "round()"), //
							transform(//
								i -> i * 5.0, //
								d -> (int) Math.round(d / 5), //
								false, true, "*5.0", "/5,round"),
							transform(//
								i -> i / 5.0, //
								d -> (int) Math.round(d * 5), //
								false, true, "/5.0", "*5,round"));
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type1, type2), transforms);
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type2, type1),
							transforms.stream().map(t -> t.reverse()).collect(Collectors.toList()));
						break;
					}
					case STRING: {
						List<TypeTransformation<Integer, String>> transforms = asList(//
							transform(i -> String.valueOf(i), s -> (int) Math.round(Double.parseDouble(s)), false, false, "toString()",
								"parseInt"));
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type1, type2), transforms);
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type2, type1),
							transforms.stream().map(t -> t.reverse()).collect(Collectors.toList()));
					}
					}
					break;
				case DOUBLE:
					switch (type2) {
					case INT: // Already done above
						break;
					case DOUBLE: {
						List<TypeTransformation<Double, Double>> transforms = asList(//
							identity(), //
							transform(d -> d + 5, d -> d - 5, false, false, "+5", "-5"), //
							transform(d -> d - 5, d -> d + 5, false, false, "-5", "+5"), //
							transform(d -> d * 5, d -> d / 5, false, false, "*5", "/5"), //
							transform(d -> d / 5, d -> d * 5, false, false, "/5", "*5"), //
							transform(d -> -d, d -> -d, false, false, "-", "-"));
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type1, type2), transforms);
						break;
					}
					case STRING: {
						List<TypeTransformation<Double, String>> transforms = asList(//
							transform(d -> stringValueOf(d), s -> Double.valueOf(s), false, false, "toString()", "parseDouble"));
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type1, type2), transforms);
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type2, type1),
							transforms.stream().map(t -> t.reverse()).collect(Collectors.toList()));
						break;
					}
					}
					break;
				case STRING:
					switch (type2) {
					case INT:
					case DOUBLE:
						break;
					case STRING: {
						List<TypeTransformation<String, String>> transforms = asList(//
							identity(), transform(s -> reverse(s), s -> reverse(s), false, false, "reverse", "reverse"));
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type1, type2), transforms);
						break;
					}
					}
				}
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
		assemble(helper);
		test(helper);
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
					Debug.d().debugIf(tri == 31 && transactionTri == 3);
					try {
						targetLink.tryModify(helper);
					} catch (RuntimeException | Error e) {
						System.err.println("Chain is " + printChain());
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
				System.err.println(
					"Integrity check failure on transaction close on link " + failedLink + " after " + modifications + " modifications");
				throw e;
			}
		}
	}

	public String printChain() {
		StringBuilder s = new StringBuilder(theChain.size()).append(':');
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
