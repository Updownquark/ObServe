package org.observe.supertest.collect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.observe.SettableValue;
import org.observe.Transformation;
import org.observe.Transformation.ReversibleTransformation;
import org.observe.Transformation.ReversibleTransformationBuilder;
import org.observe.Transformation.ReversibleTransformationPrecursor;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.BiTypeTransformation;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.ObservableChainTester;
import org.observe.supertest.TestValueType;
import org.observe.supertest.TypeTransformation;
import org.qommons.LambdaUtils;
import org.qommons.Transactable;
import org.qommons.ValueHolder;
import org.qommons.testing.TestHelper;
import org.qommons.testing.TestHelper.RandomAction;

import com.google.common.reflect.TypeToken;

/**
 * Tests {@link org.observe.collect.ObservableCollection.CollectionDataFlow#transform(TypeToken, Function)}
 *
 * @param <S> The source link type
 * @param <V> The type of the value to combine with the source values
 * @param <T> The type of the values produced by the combination
 */
public class CombinedCollectionLink<S, V, T> extends AbstractMappedCollectionLink<S, T> {
	/** Generates {@link CombinedCollectionLink}s */
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator.CollectionLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			if (!CombinedCollectionLink.supportsTransform(sourceCL.getDef().type, null, targetType, true, false))
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableCollectionLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			BiTypeTransformation<T, ?, X> transform = CombinedCollectionLink.transform(sourceCL.getDef().type, null, targetType, helper,
				true, false);
			return deriveLink(path, sourceCL, transform, helper);
		}

		private <S, V, T> CombinedCollectionLink<S, V, T> deriveLink(String path, ObservableCollectionLink<?, S> sourceCL,
			BiTypeTransformation<S, V, T> transform, TestHelper helper) {
			int valueCount = helper.getInt(1, 5); // Between 1 and 4, inclusive
			List<SettableValue<V>> values = new ArrayList<>(valueCount);
			for (int i = 0; i < valueCount; i++)
				values.add(SettableValue.build((TypeToken<V>) transform.getValueType().getType()).build());
			Function<TestHelper, V> valueSupplier = (Function<TestHelper, V>) ObservableChainTester.SUPPLIERS.get(transform.getValueType());
			for (int i = 0; i < valueCount; i++)
				values.get(i).set(valueSupplier.apply(helper), null);
			Function<List<V>, V> valueCombination = getValueCombination(transform.getValueType());

			BiFunction<S, Transformation.TransformationValues<? extends S, ? extends T>, T> map = LambdaUtils.printableBiFn((s, cv) -> {
				List<V> valueList = (List<V>) Arrays.asList(new Object[values.size()]);
				for (int i = 0; i < values.size(); i++)
					valueList.set(i, cv.get(values.get(i)));
				V combinedV = valueCombination.apply(valueList);
				return transform.map(s, combinedV);
			}, transform::toString, null);
			BiFunction<T, Transformation.TransformationValues<? extends S, ? extends T>, S> reverse = (s, cv) -> {
				List<V> valueList = (List<V>) Arrays.asList(new Object[values.size()]);
				for (int i = 0; i < values.size(); i++)
					valueList.set(i, cv.get(values.get(i)));
				V combinedV = valueCombination.apply(valueList);
				return transform.reverse(s, combinedV);
			};
			boolean needsUpdateReeval = !sourceCL.getDef().checkOldValues;
			boolean cache = helper.getBoolean(.75);
			boolean withReverse = transform.supportsReverse() && helper.getBoolean(.95);
			boolean fireIfUnchanged = needsUpdateReeval || helper.getBoolean();
			boolean reEvalOnUpdate = needsUpdateReeval || helper.getBoolean();
			boolean oneToMany = transform.isOneToMany();
			boolean manyToOne = transform.isManyToOne();
			TypeToken<T> type = (TypeToken<T>) transform.getTargetType().getType();
			ValueHolder<Transformation<S, T>> options = new ValueHolder<>();
			Function<ReversibleTransformationPrecursor<S, T, ?>, Transformation<S, T>> combination = combine -> {
				ReversibleTransformationPrecursor<S, T, ?> combinePre = combine.cache(cache).manyToOne(manyToOne).oneToMany(oneToMany)//
					.fireIfUnchanged(fireIfUnchanged).reEvalOnUpdate(reEvalOnUpdate);
				ReversibleTransformationBuilder<S, T, ?> builder = combinePre.combineWith(values.get(0));
				for (int i = 1; i < values.size(); i++)
					builder = builder.combineWith(values.get(i));

				Transformation<S, T> def;
				if (withReverse)
					def = builder.build(map).replaceSourceWith(reverse);
				else
					def = builder.build(map);
				options.accept(def);
				return def;
			};
			CollectionDataFlow<?, ?, T> oneStepFlow = sourceCL.getCollection().flow().transform(type, combination);
			CollectionDataFlow<?, ?, T> multiStepFlow = sourceCL.getDef().multiStepFlow.transform(type, combination);

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
	private final Transformation<S, T> theOptions;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param def The collection definition for this link
	 * @param helper The randomness to use to initialize this link
	 * @param operation The combination operation
	 * @param values The list of values to combine with the source
	 * @param valueCombination The operation to combine all the values into a single value for the combination
	 * @param valueSupplier The value supplier for the combined values
	 * @param options The options used to define the combination
	 */
	public CombinedCollectionLink(String path, ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper, BiTypeTransformation<S, V, T> operation, List<SettableValue<V>> values, Function<List<V>, V> valueCombination,
		Function<TestHelper, V> valueSupplier, Transformation<S, T> options) {
		super(path, sourceLink, def, helper, options.isCached(), isInexactReversible(options));
		theOperation = operation;
		theValues = values;
		theValueCombination = valueCombination;
		theValueSupplier = valueSupplier;

		theOptions = options;
	}

	/** @return The sum of all of this link's combination values */
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
		return theOptions instanceof ReversibleTransformation;
	}

	@Override
	protected Transactable getLocking() {
		ArrayList<Transactable> locking = new ArrayList<>(theValues.size() + 1);
		locking.addAll(theValues);
		locking.add(super.getLocking());
		return Transactable.combine(locking);
	}

	@Override
	public double getModificationAffinity() {
		return super.getModificationAffinity() + 2;
	}

	@Override
	public void tryModify(RandomAction action, TestHelper helper) {
		super.tryModify(action, helper);
		action.or(2, () -> {
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

	/**
	 * Called when an input value is changed
	 *
	 * @param oldCombinedValue The previous combination of all the input values
	 * @param newCombinedValue The new, current combination of all the input values
	 */
	protected void expectValueChange(V oldCombinedValue, V newCombinedValue) {
		for (CollectionLinkElement<S, T> element : getElements()) {
			T newValue = theOperation.map(element.getFirstSource().getValue(), newCombinedValue);
			element.expectSet(newValue);
		}
	}

	/**
	 * @param sourceType The type to transform
	 * @param valueType The value type of the transform, or null if unspecified
	 * @param targetType The target type of the transform, or null if unspecified
	 * @param allowManyToOne Whether {@link TypeTransformation#isManyToOne() many-to-one} transformations are acceptable
	 * @param requireReversible Whether acceptable mappings must {@link TypeTransformation#supportsReverse() support reversal}
	 * @return Whether any acceptable transformations exist
	 */
	public static boolean supportsTransform(TestValueType sourceType, TestValueType valueType, TestValueType targetType,
		boolean allowManyToOne, boolean requireReversible) {
		return getTransformStream(sourceType, valueType, targetType, allowManyToOne, requireReversible).limit(1).count() == 1;
	}

	private static Stream<BiTypeTransformation<?, ?, ?>> getTransformStream(TestValueType sourceType, TestValueType valueType,
		TestValueType targetType, boolean allowManyToOne, boolean requireReversible) {
		return TYPE_TRANSFORMATIONS.stream().filter(transform -> {
			if (!allowManyToOne && transform.isManyToOne())
				return false;
			else if (requireReversible && !transform.supportsReverse())
				return false;
			else if (sourceType != null && transform.getSourceType() != sourceType)
				return false;
			else if (targetType != null && transform.getTargetType() != targetType)
				return false;
			else if (valueType != null && transform.getValueType() != valueType)
				return false;
			else
				return true;
		});
	}

	/**
	 * @param <E> The source type
	 * @param <T> The target type
	 * @param type The source type to transform
	 * @param valueType The value type of the transform, or null if unspecified
	 * @param targetType The target type of the transform, or null if unspecified
	 * @param helper The randomness to use to get a random transformation
	 * @param allowManyToOne Whether {@link TypeTransformation#isManyToOne() many-to-one} transformations are acceptable
	 * @param requireReversible Whether acceptable mappings must {@link TypeTransformation#supportsReverse() support reversal}
	 * @return The transformation
	 */
	public static <E, V, T> BiTypeTransformation<E, V, T> transform(TestValueType type, TestValueType valueType, TestValueType targetType,
		TestHelper helper,
		boolean allowManyToOne, boolean requireReversible) {
		List<BiTypeTransformation<?, ?, ?>> transforms = getTransformStream(type, valueType, targetType, allowManyToOne, requireReversible)
			.collect(Collectors.toList());
		if (transforms.isEmpty())
			throw new UnsupportedOperationException();
		BiTypeTransformation<?, ?, ?> transform;
		if (transforms.size() == 1)
			transform = transforms.get(0);
		else
			transform = transforms.get(helper.getInt(0, transforms.size()));
		return (BiTypeTransformation<E, V, T>) transform;
	}

	@Override
	public String toString() {
		String str = "combined:" + theOperation + toList(theValues);
		if (!(theOptions instanceof ReversibleTransformation))
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

	private static final List<BiTypeTransformation<?, ?, ?>> TYPE_TRANSFORMATIONS;
	static {
		TYPE_TRANSFORMATIONS = new ArrayList<>();
		for (TestValueType type1 : TestValueType.values()) {
			for (TestValueType type2 : TestValueType.values()) {
				switch (type1) {
				case INT:
					switch (type2) {
					case INT: {
						supportTransforms(type1, type2, true, //
							CombinedCollectionLink.<Integer, Integer, Integer> transform(type1, type1, type2, (i1, i2) -> i1 + i2,
								(i1, i2) -> i1 - i2, false, false, "+", "-"));
						supportTransforms(type1, type2, false, //
							CombinedCollectionLink.<Integer, Integer, Integer> transform(type1, type1, type2, (i1, i2) -> i1 * i2,
								(i1, i2) -> {
									if (i2 != 0)
										return i1 / i2;
									else if (i1 >= 0)
										return Integer.MAX_VALUE;
									else
										return Integer.MIN_VALUE;
								}, false, true, "*", "/")//
							);
						break;
					}
					case DOUBLE: {
						supportTransforms(type1, type2, true, //
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
						CombinedCollectionLink.<Double, Double> supportTransforms(type1, type2, true, //
							// The a + b - b is not always equal to a due to precision losses
							CombinedCollectionLink.<Double, Double, Double> transform(type1, type1, type2, (d1, d2) -> noNeg0(d1 + d2),
								(d1, d2) -> noNeg0(d1 - d2), true, true, "+", "-"), //
							// Same with a * b / b
							CombinedCollectionLink.<Double, Double, Double> transform(type1, type1, type2, (d1, d2) -> noNeg0(d1 * d2),
								(d1, d2) -> noNeg0(d1 / d2), true, true, "*", "/") //
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
						supportTransforms(type1, type2, true, //
							CombinedCollectionLink.<String, String, String> transform(type1, type1, type2,
								(s1, s2) -> combineStrings(s1, s2), null, false, false, "append", null), //
							CombinedCollectionLink.<String, String, String> transform(type1, type1, type2,
								(s1, s2) -> combineStrings(s2, s1), null, false, false, "prepend", null)//
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

	static String combineStrings(String s1, String s2) {
		StringBuilder str = new StringBuilder();
		if (s2.length() > 0 && s2.charAt(0) == '-') {
			if (s1.length() == 0 || s1.charAt(0) != '-')
				str.append('-');
			str.append(s1).append(s2, 1, s2.length());
		} else
			str.append(s1).append(s2);
		return str.toString();
	}

	private static <S, T> void supportTransforms(TestValueType type1, TestValueType type2, boolean withReverse,
		BiTypeTransformation<S, ?, T>... transforms) {
		for (BiTypeTransformation<S, ?, T> transform : transforms) {
			TYPE_TRANSFORMATIONS.add(transform);
			if (withReverse && transform.supportsReverse())
				TYPE_TRANSFORMATIONS.add(transform.reverse());
		}
	}

	/**
	 * @param valueType The value type
	 * @return A function to combine a list of values of the given type into a single value
	 */
	public static <V> Function<List<V>, V> getValueCombination(TestValueType valueType) {
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

	/**
	 * @param values The list of settable values
	 * @return The list of values
	 */
	public static <V> List<V> toList(List<SettableValue<V>> values) {
		List<V> list = (List<V>) Arrays.asList(new Object[values.size()]);
		for (int i = 0; i < values.size(); i++)
			list.set(i, values.get(i).get());
		return list;
	}
}
