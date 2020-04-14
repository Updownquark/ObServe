package org.observe.supertest.dev2.links;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.SettableValue;
import org.observe.collect.Combination.CombinationPrecursor;
import org.observe.collect.Combination.CombinedCollectionBuilder2;
import org.observe.collect.Combination.CombinedFlowDef;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev2.ChainLinkGenerator;
import org.observe.supertest.dev2.ObservableChainLink;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.OneToOneCollectionLink;
import org.observe.supertest.dev2.TestValueType;
import org.qommons.TestHelper;

import com.google.common.reflect.TypeToken;

public class CombinedCollectionLink<S, V, T> extends OneToOneCollectionLink<S, T> {
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			if (!CombinedCollectionLink.supportsTransform(sourceCL.getDef().type, true, false))
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			BiTypeTransformation<T, ?, X> transform = CombinedCollectionLink.transform(sourceCL.getDef().type, helper, true, false);
			return deriveLink(path, sourceCL, transform, helper);
		}

		private <S, V, T> CombinedCollectionLink<S, V, T> deriveLink(String path, ObservableCollectionLink<?, S> sourceCL,
			BiTypeTransformation<S, V, T> transform, TestHelper helper) {
			SettableValue<V> value = SettableValue.build((TypeToken<V>) transform.getValueType().getType()).safe(false).build();

			boolean needsUpdateReeval = !sourceCL.getDef().checkOldValues;
			boolean cache = helper.getBoolean(.75);
			boolean withReverse = transform.supportsReverse() && helper.getBoolean(.95);
			boolean fireIfUnchanged = needsUpdateReeval || helper.getBoolean();
			boolean reEvalOnUpdate = needsUpdateReeval || helper.getBoolean();
			boolean oneToMany = transform.isOneToMany();
			boolean manyToOne = transform.isManyToOne();
			TypeToken<T> type = (TypeToken<T>) transform.getTargetType().getType();
			Function<CombinationPrecursor<S, T>, CombinedFlowDef<S, T>> combination = combine -> {
				CombinedCollectionBuilder2<S, V, T> combineVal = combine.with(value).cache(cache).manyToOne(manyToOne).oneToMany(oneToMany);
				if (transform.supportsReverse())
					combineVal.withReverse(transform::reverse);
				return combineVal.build(vals -> transform.map(vals.getElement(), vals.get(value)));
			};
			CollectionDataFlow<?, ?, T> oneStepFlow = sourceCL.getCollection().flow().combine(type, combination);
			CollectionDataFlow<?, ?, T> multiStepFlow = sourceCL.getDef().multiStepFlow.combine(type, combination);

			// TODO Auto-generated method stub
		}
	};

	private final BiTypeTransformation<S, V, T> theOperation;
	private final SettableValue<V> theValue;

	@Override
	protected T map(S sourceValue) {
		return theOperation.map(sourceValue, theValue.get());
	}

	@Override
	protected S reverse(T value) {
		return theOperation.reverse(value, theValue.get());
	}

	@Override
	protected boolean isReversible() {
		return theOperation.supportsReverse();
	}

	@Override
	public boolean isAcceptable(T value) {
		S reversed;
		try {
			reversed = theOperation.reverse(value, theValue.get());
		} catch (RuntimeException e) {
			return false;
		}
		return getSourceLink().isAcceptable(reversed);
	}

	@Override
	public T getUpdateValue(T value) {
		if (theOptions.isCached() || !isReversible())
			return value;
		else
			return map(getSourceLink().getUpdateValue(reverse(value)));
	}

	public interface BiTypeTransformation<S, V, T> {
		TestValueType getSourceType();

		TestValueType getValueType();

		TestValueType getTargetType();

		T map(S source, V value);

		boolean supportsReverse();

		S reverse(T mapped, V value);

		boolean isManyToOne();

		boolean isOneToMany();

		String reverseName();

		default BiTypeTransformation<T, V, S> reverse() {
			if (!supportsReverse())
				throw new UnsupportedOperationException();
			BiTypeTransformation<S, V, T> outer = this;
			return new BiTypeTransformation<T, V, S>() {
				@Override
				public TestValueType getSourceType() {
					return outer.getTargetType();
				}

				@Override
				public TestValueType getValueType() {
					return outer.getValueType();
				}

				@Override
				public TestValueType getTargetType() {
					return outer.getSourceType();
				}

				@Override
				public S map(T source, V value) {
					return outer.reverse(source, value);
				}

				@Override
				public boolean supportsReverse() {
					return true;
				}

				@Override
				public T reverse(S mapped, V value) {
					return outer.map(mapped, value);
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
				public BiTypeTransformation<S, V, T> reverse() {
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

	public static boolean supportsTransform(TestValueType sourceType, boolean allowManyToOne, boolean requireReversible) {
		List<? extends BiTypeTransformation<?, ?, ?>> transforms = TYPE_TRANSFORMATIONS.get(sourceType);
		if (transforms == null || transforms.isEmpty())
			return false;
		if (!allowManyToOne || requireReversible) {
			boolean supported = false;
			for (BiTypeTransformation<?, ?, ?> transform : transforms) {
				if (!allowManyToOne && transform.isManyToOne()) {} else if (requireReversible && !transform.supportsReverse()) {} else {
					supported = true;
					break;
				}
			}
			return supported;
		}
		return true;
	}

	public static <E, T> BiTypeTransformation<E, ?, T> transform(TestValueType type, TestHelper helper, boolean allowManyToOne,
		boolean requireReversible) {
		List<? extends BiTypeTransformation<E, ?, ?>> transforms = (List<? extends BiTypeTransformation<E, ?, ?>>) TYPE_TRANSFORMATIONS
			.get(type);
		if ((!allowManyToOne || requireReversible) && !supportsTransform(type, allowManyToOne, requireReversible))
			throw new UnsupportedOperationException();
		BiTypeTransformation<E, ?, ?> transform;
		while (true) {
			if (transforms.size() == 1)
				transform = transforms.get(0);
			else
				transform = transforms.get(helper.getInt(0, transforms.size()));
			if (!allowManyToOne && transform.isManyToOne())
				continue;
			else if (requireReversible && !transform.supportsReverse())
				continue;
			else
				break;
		}
		return (BiTypeTransformation<E, ?, T>) transform;
	}

	public static <E, T> BiTypeTransformation<E, ?, T> transform(TestValueType sourceType, TestValueType destType, TestHelper helper,
		boolean allowManyToOne, boolean requireReversible) {
		List<? extends BiTypeTransformation<E, ?, ?>> transforms = (List<? extends BiTypeTransformation<E, ?, ?>>) TYPE_TRANSFORMATIONS
			.get(sourceType).stream().filter(transform -> transform.getTargetType() == destType).filter(transform -> {
				if (!allowManyToOne && transform.isManyToOne())
					return false;
				else if (requireReversible && !transform.supportsReverse())
					return false;
				else
					return true;
			}).collect(Collectors.toList());
		BiTypeTransformation<E, ?, ?> transform = transforms.get(helper.getInt(0, transforms.size()));
		return (BiTypeTransformation<E, ?, T>) transform;
	}

	@Override
	public String toString() {
		String str = theOperation + " " + theValue.get();
		if (theOptions.getReverse() == null)
			str += ", irreversible";
		return str + ")";
	}

	private static <S, V, T> BiTypeTransformation<S, V, T> transform(TestValueType sourceType, TestValueType valueType, TestValueType type,
		BiFunction<S, V, T> map, BiFunction<T, V, S> reverse, boolean manyToOne, boolean oneToMany, String name, String reverseName) {
		return new BiTypeTransformation<S, V, T>() {
			@Override
			public TestValueType getSourceType() {
				return sourceType;
			}

			@Override
			public TestValueType getValueType() {
				return valueType;
			}

			@Override
			public TestValueType getTargetType() {
				return type;
			}

			@Override
			public T map(S source, V value) {
				return map.apply(source, value);
			}

			@Override
			public boolean supportsReverse() {
				return reverse != null;
			}

			@Override
			public S reverse(T mapped, V value) {
				if (reverse == null)
					throw new UnsupportedOperationException();
				return reverse.apply(mapped, value);
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
		for (int i = start; i < c.length / 2; i++) {
			char temp = c[i];
			int opposite = c.length - i - 1;
			c[i] = c[opposite];
			c[opposite] = temp;
		}
		return new String(c);
	}

	private static final Map<TestValueType, List<? extends BiTypeTransformation<?, ?, ?>>> TYPE_TRANSFORMATIONS;
	static {
		TYPE_TRANSFORMATIONS = new HashMap<>();
		for (TestValueType type1 : TestValueType.values()) {
			for (TestValueType type2 : TestValueType.values()) {
				switch (type1) {
				case INT:
					switch (type2) {
					case INT: {
						supportTransforms(type1, type2, //
							CombinedCollectionLink.<Integer, Integer, Integer> transform(type1, type1, type2, (i1, i2) -> i1 + i2,
								(i1, i2) -> i1 - i2, false, false, "+", "-"), //
							CombinedCollectionLink.<Integer, Integer, Integer> transform(type1, type1, type2, (i1, i2) -> i1 * i2,
								(i1, i2) -> i1 / i2, false, true, "*", "/")//
							);
						break;
					}
					case DOUBLE: {
						supportTransforms(type1, type2, //
							CombinedCollectionLink.<Integer, Double, Double> transform(type1, type2, type2, //
								(i, d) -> noNeg0(i + d), //
								(d1, d2) -> (int) Math.round(d1 - d2), //
								false, true, "+d", "-,round()"), //
							CombinedCollectionLink.<Integer, Double, Double> transform(type1, type2, type2, //
								(i, d) -> noNeg0(i - d), //
								(d1, d2) -> (int) Math.round(d1 + d2), //
								false, true, "-d", "+,round()"), //
							CombinedCollectionLink.<Integer, Double, Double> transform(type1, type2, type2, //
								(i, d) -> noNeg0(i * d), //
								(d1, d2) -> (int) Math.round(d1 / d2), //
								false, true, "*d", "/,round()"), //
							CombinedCollectionLink.<Integer, Double, Double> transform(type1, type2, type2, //
								(i, d) -> noNeg0(i / d), //
								(d1, d2) -> (int) Math.round(d1 * d2), //
								false, true, "/d", "*,round"));
						break;
					}
					case STRING:
					case BOOLEAN:
						break;
					}
					break;
				case DOUBLE:
					switch (type2) {
					case INT: // Already done above
						break;
					case DOUBLE: {
						CombinedCollectionLink.<Double, Double> supportTransforms(type1, type2, //
							// The a + b - b is not always equal to a due to precision losses
							CombinedCollectionLink.<Double, Double, Double> transform(type1, type1, type2, (d1, d2) -> noNeg0(d1 + d2),
								(d1, d2) -> noNeg0(d1 - d2), true, true, "+", "-"), //
							CombinedCollectionLink.<Double, Double, Double> transform(type1, type1, type2, (d1, d2) -> noNeg0(d1 * d2),
								(d1, d2) -> noNeg0(d1 / d2), false, true, "*", "/") //
							);
						break;
					}
					case STRING:
					case BOOLEAN:
						break;
					}
					break;
				case STRING:
					switch (type2) {
					case INT:
					case DOUBLE:
						break;
					case STRING: {
						supportTransforms(type1, type2, //
							CombinedCollectionLink.<String, String, String> transform(type1, type1, type2, (s1, s2) -> s1 + s2, null, false,
								false, "append", null), //
							CombinedCollectionLink.<String, String, String> transform(type1, type1, type2, (s1, s2) -> s2 + s1, null, false,
								false, "prepend", null)//
							);
						break;
					}
					case BOOLEAN:
						break;
					}
					break;
				case BOOLEAN:
					break;
				}
			}
		}
	}

	// Some double->double arithmetic operations whose result is zero are somewhat indeterminate which zero they return.
	// Since -0.0 != 0.0, this can cause problems, for example, when the result of a + b - b is expected to be a.
	static double noNeg0(double d) {
		if (d == -0.0)
			return 0.0;
		return d;
	}

	private static <S, T> void supportTransforms(TestValueType type1, TestValueType type2, BiTypeTransformation<S, ?, T>... transforms) {
		List<BiTypeTransformation<S, ?, T>> forward = (List<BiTypeTransformation<S, ?, T>>) TYPE_TRANSFORMATIONS.computeIfAbsent(type1,
			t -> new LinkedList<>());
		List<BiTypeTransformation<T, ?, S>> backward = (List<BiTypeTransformation<T, ?, S>>) TYPE_TRANSFORMATIONS.computeIfAbsent(type2,
			t -> new LinkedList<>());
		for (BiTypeTransformation<S, ?, T> transform : transforms) {
			forward.add(transform);
			if (backward != null && transform.supportsReverse())
				backward.add(transform.reverse());
		}
	}

	private static String stringValueOf(double d) {
		String str = String.valueOf(d);
		if (str.endsWith(".0"))
			str = str.substring(0, str.length() - 2);
		return str;
	}
}
