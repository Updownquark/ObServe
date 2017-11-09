package org.observe.supertest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.collect.DefaultObservableCollection;
import org.qommons.BiTuple;
import org.qommons.TestHelper;
import org.qommons.TestHelper.Testable;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

public class ObservableChainTester implements Testable {
	static enum TestValueType {
		INT(TypeToken.of(int.class)), DOUBLE(TypeToken.of(double.class)), STRING(TypeToken.of(String.class));

		private final TypeToken<?> type;

		private TestValueType(TypeToken<?> type) {
			this.type = type;
		}
	}

	private interface TypeTransformation<E, T> {
		T map(E source);

		E reverse(T mapped);

		boolean isEquivalent();

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
				public boolean isEquivalent() {
					return outer.isEquivalent();
				}
			};
		}
	}

	private static TestValueType nextType(TestHelper helper) {
		return TestValueType.values()[helper.getInt(0, TestValueType.values().length)];
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
			public boolean isEquivalent() {
				return true;
			}
		};
	}

	private static <E, T> TypeTransformation<E, T> transform(Function<E, T> map, Function<T, E> reverse, boolean equivalent) {
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
			public boolean isEquivalent() {
				return equivalent;
			}
		};
	}

	private static <E, T> List<TypeTransformation<E, T>> asList(TypeTransformation<E, T>... transforms) {
		return Arrays.asList(transforms);
	}

	private static String reverse(String s) {
		char[] c = s.toCharArray();
		for (int i = 0; i <= c.length / 2; i++) {
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
							transform(i -> i + 5, i -> i - 5, true), transform(i -> i - 5, i -> i + 5, true), //
							transform(i -> i * 5, i -> i / 5, false), transform(i -> i / 5, i -> i * 5, false));
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type1, type2), transforms);
						break;
					}
					case DOUBLE: {
						List<TypeTransformation<Integer, Double>> transforms = asList(//
							transform(i -> i * 1.0, d -> (int) Math.round(d), false), //
							transform(i -> i * 5.0, d -> (int) Math.round(d / 5), false),
							transform(i -> i / 5.0, d -> (int) Math.round(d * 5), false));
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type1, type2), transforms);
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type2, type1),
							transforms.stream().map(t -> t.reverse()).collect(Collectors.toList()));
						break;
					}
					case STRING: {
						List<TypeTransformation<Integer, String>> transforms = asList(//
							transform(i -> String.valueOf(i), s -> Integer.valueOf(s), true));
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
							transform(d -> d + 5, d -> d - 5, true), transform(d -> d - 5, d -> d + 5, true), //
							transform(d -> d * 5, d -> d / 5, true), transform(d -> d / 5, d -> d * 5, true));
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type1, type2), transforms);
						break;
					}
					case STRING: {
						List<TypeTransformation<Double, String>> transforms = asList(//
							transform(d -> String.valueOf(d), s -> Double.valueOf(s), true));
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
							identity(), transform(s -> reverse(s), s -> reverse(s), true));
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type1, type2), transforms);
						break;
					}
					}
				}
			}
		}
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

	private <E> void assemble(TestHelper helper) {
		//Tend toward smaller chain lengths, but allow longer ones occasionally
		int chainLength = helper.getInt(2, helper.getInt(2, MAX_CHAIN_LENGTH));
		ObservableChainLink<?> initLink = createInitialLink(helper);
		theChain.add(initLink);
		while (theChain.size() < chainLength)
			theChain.add(theChain.get(theChain.size() - 1).derive(helper));
	}

	private <E> ObservableChainLink<?> createInitialLink(TestHelper helper) {
		int linkTypes = 3;
		switch (helper.getInt(0, linkTypes)) {
		case 0:
			// TODO Uncomment this when CircularArrayList is working
			// return new ObservableCollectionLinkTester<>(null, new DefaultObservableCollection<>((TypeToken<E>) type.type,
			// CircularArrayList.build().build()));
		case 1:
			return new ObservableCollectionLinkTester<>(null,
				new DefaultObservableCollection<>((TypeToken<E>) type.type, new BetterTreeList<>(true)));
		case 2:
			return new ObservableCollectionLinkTester<>(null,
				new DefaultObservableCollection<>((TypeToken<E>) type.type, new BetterTreeSet<>(true, randomComparator(type, helper))));
			// TODO ObservableValue
			// TODO ObservableMultiMap
			// TODO ObservableMap
			// TODO ObservableTree?
			// TODO ObservableGraph?
		}
		throw new IllegalStateException();
	}

	private void test(TestHelper helper) {
		int tries = 1000;
		for (int tri = 0; tri < tries; tri++) {
			int linkIndex = helper.getInt(0, theChain.size());
			theChain.get(linkIndex).tryModify(helper);
			for (ObservableChainLink<?> link : theChain)
				link.check();
		}
	}
}