package org.observe.supertest.dev2.links;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.SettableValue;
import org.observe.collect.Combination;
import org.observe.collect.Combination.CombinationPrecursor;
import org.observe.collect.Combination.CombinedFlowDef;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev2.ChainLinkGenerator;
import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.CollectionSourcedLink;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableChainLink;
import org.observe.supertest.dev2.ObservableChainTester;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.TestValueType;
import org.qommons.TestHelper;
import org.qommons.TestHelper.RandomAction;
import org.qommons.ValueHolder;

import com.google.common.reflect.TypeToken;

public class CombinedCollectionLink<S, V, T> extends AbstractMappedCollectionLink<S, T> {
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
			int valueCount = helper.getInt(1, 5); // Between 1 and 4, inclusive
			List<SettableValue<V>> values = new ArrayList<>(valueCount);
			for (int i = 0; i < valueCount; i++)
				values.add(SettableValue.build((TypeToken<V>) transform.getValueType().getType()).safe(false).build());
			Function<TestHelper, V> valueSupplier = (Function<TestHelper, V>) ObservableChainTester.SUPPLIERS.get(transform.getValueType());
			for (int i = 0; i < valueCount; i++)
				values.get(i).set(valueSupplier.apply(helper), null);
			Function<List<V>, V> valueCombination = getValueCombination(transform.getValueType());

			Function<Combination.CombinedValues<? extends S>, T> map = cv -> {
				List<V> valueList = (List<V>) Arrays.asList(new Object[values.size()]);
				for (int i = 0; i < values.size(); i++)
					valueList.set(i, cv.get(values.get(i)));
				V combinedV = valueCombination.apply(valueList);
				return transform.map(cv.getElement(), combinedV);
			};
			Function<Combination.CombinedValues<? extends T>, S> reverse = cv -> {
				List<V> valueList = (List<V>) Arrays.asList(new Object[values.size()]);
				for (int i = 0; i < values.size(); i++)
					valueList.set(i, cv.get(values.get(i)));
				V combinedV = valueCombination.apply(valueList);
				return transform.reverse(cv.getElement(), combinedV);
			};
			boolean needsUpdateReeval = !sourceCL.getDef().checkOldValues;
			boolean cache = helper.getBoolean(.75);
			boolean withReverse = transform.supportsReverse() && helper.getBoolean(.95);
			boolean fireIfUnchanged = needsUpdateReeval || helper.getBoolean();
			boolean reEvalOnUpdate = needsUpdateReeval || helper.getBoolean();
			boolean oneToMany = transform.isOneToMany();
			boolean manyToOne = transform.isManyToOne();
			TypeToken<T> type = (TypeToken<T>) transform.getTargetType().getType();
			ValueHolder<Combination.CombinedFlowDef<S, T>> options = new ValueHolder<>();
			Function<CombinationPrecursor<S, T>, CombinedFlowDef<S, T>> combination = combine -> {
				Combination.CombinationPrecursor<S, T> combinePre = combine.cache(cache).manyToOne(manyToOne).oneToMany(oneToMany)//
					.fireIfUnchanged(fireIfUnchanged).reEvalOnUpdate(reEvalOnUpdate);
				org.qommons.BreakpointHere.breakpoint();
				Combination.CombinedCollectionBuilder<S, T> builder = combinePre.with(values.get(0));
				for (int i = 1; i < values.size(); i++)
					builder = builder.with(values.get(i));

				if (withReverse)
					builder.withReverse(reverse);
				Combination.CombinedFlowDef<S, T> def = builder.build(map);
				options.accept(def);
				return def;
			};
			CollectionDataFlow<?, ?, T> oneStepFlow = sourceCL.getCollection().flow().combine(type, combination);
			CollectionDataFlow<?, ?, T> multiStepFlow = sourceCL.getDef().multiStepFlow.combine(type, combination);

			ObservableCollectionTestDef<T> newDef = new ObservableCollectionTestDef<>(transform.getTargetType(), oneStepFlow, multiStepFlow,
				sourceCL.getDef().orderImportant, cache);
			return new CombinedCollectionLink<>(path, sourceCL, newDef, helper, transform, values, valueCombination, valueSupplier,
				options.get());
		}
	};

	private final BiTypeTransformation<S, V, T> theOperation;
	private final List<SettableValue<V>> theValues;
	private final Function<List<V>, V> theValueCombination;
	private final Function<TestHelper, V> theValueSupplier;
	private final CombinedFlowDef<S, T> theOptions;

	public CombinedCollectionLink(String path, ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper, BiTypeTransformation<S, V, T> operation, List<SettableValue<V>> values, Function<List<V>, V> valueCombination,
		Function<TestHelper, V> valueSupplier, CombinedFlowDef<S, T> options) {
		super(path, sourceLink, def, helper, options.isCached());
		theOperation = operation;
		theValues = values;
		theValueCombination = valueCombination;
		theValueSupplier = valueSupplier;

		theOptions = options;
	}

	protected V getValueSum() {
		return theValueCombination.apply(toList(theValues));
	}

	@Override
	protected T map(S sourceValue) {
		return theOperation.map(sourceValue, getValueSum());
	}

	@Override
	protected S reverse(T value) {
		if (!isReversible())
			throw new IllegalStateException();
		return theOperation.reverse(value, getValueSum());
	}

	@Override
	protected boolean isReversible() {
		return theOptions.getReverse() != null;
	}

	@Override
	public double getModificationAffinity() {
		return super.getModificationAffinity() + 2;
	}

	@Override
	public void tryModify(RandomAction action, TestHelper helper) {
		super.tryModify(action, helper);
		action.or(1, () -> {
			int targetValue = helper.getInt(0, theValues.size());
			V newValue = theValueSupplier.apply(helper);

			V oldValue = theValues.get(targetValue).get();
			V oldCombinedValue = getValueSum();
			theValues.get(targetValue).set(newValue, null);
			V newCombinedValue = getValueSum();
			if (helper.isReproducing())
				System.out.println(
					"Value[" + targetValue + "] " + oldValue + "->" + newValue + "; total " + oldCombinedValue + "->" + newCombinedValue);
			expectValueChange(oldCombinedValue, newCombinedValue);
		});
	}

	protected void expectValueChange(V oldCombinedValue, V newCombinedValue) {
		for (CollectionLinkElement<S, T> element : getElements()) {
			T oldValue = element.getValue();
			T newValue = theOperation.map(element.getFirstSource().getValue(), newCombinedValue);
			element.setValue(newValue);
			for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
				derived.expectFromSource(new ExpectedCollectionOperation<>(element, CollectionOpType.set, oldValue, newValue));
		}
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
		String str = "combined:" + theOperation + toList(theValues);
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
								(d1, d2) -> noNeg0(d1 / d2), true, false, "*", "/") //
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

	static <V> Function<List<V>, V> getValueCombination(TestValueType valueType) {
		switch (valueType) {
		case INT:
			return values -> (V) Integer.valueOf(((List<Integer>) values).stream().mapToInt(Integer::intValue).sum());
		case DOUBLE:
			return values -> (V) Double.valueOf(((List<Double>) values).stream().mapToDouble(Double::doubleValue).sum());
		case BOOLEAN:
			return values -> {
				boolean ret = false;
				for (V v : values)
					ret ^= ((Boolean) v).booleanValue();
				return (V) Boolean.valueOf(ret);
			};
		case STRING:
			return values -> {
				StringBuilder sb = new StringBuilder();
				for (V val : values)
					sb.append(val);
				return (V) sb.toString();
			};
		}
		throw new IllegalStateException();
	}

	static <V> List<V> toList(List<SettableValue<V>> values) {
		List<V> list = (List<V>) Arrays.asList(new Object[values.size()]);
		for (int i = 0; i < values.size(); i++)
			list.set(i, values.get(i).get());
		return list;
	}
}
