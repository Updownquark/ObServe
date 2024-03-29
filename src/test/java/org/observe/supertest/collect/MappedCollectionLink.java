package org.observe.supertest.collect;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.SettableValue;
import org.observe.Transformation;
import org.observe.Transformation.MaybeReversibleMapping;
import org.observe.Transformation.TransformationPrecursor;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.TestValueType;
import org.observe.supertest.TypeTransformation;
import org.qommons.LambdaUtils;
import org.qommons.Transactable;
import org.qommons.ValueHolder;
import org.qommons.testing.TestHelper;
import org.qommons.testing.TestHelper.RandomAction;

import com.google.common.reflect.TypeToken;

/**
 * Tests {@link org.observe.collect.ObservableCollection.CollectionDataFlow#map(TypeToken, Function)}
 *
 * @param <S> The type of the source link's collection
 * @param <T> The type of this link's collection
 */
public class MappedCollectionLink<S, T> extends AbstractMappedCollectionLink<S, T> {
	/** Generates {@link MappedCollectionLink}s */
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator.CollectionLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			if (!MappedCollectionLink.supportsTransform(sourceCL.getDef().type, targetType, true, false))
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableCollectionLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			TypeTransformation<T, X> transform = MappedCollectionLink.transform(sourceCL.getDef().type, targetType, helper, true, false);
			SettableValue<TypeTransformation<T, X>> txValue = SettableValue
				.build((TypeToken<TypeTransformation<T, X>>) (TypeToken<?>) new TypeToken<Object>() {
				}).build();
			txValue.set(transform, null);
			boolean variableMap = helper.getBoolean();
			CollectionDataFlow<?, ?, T> oneStepFlow = sourceCL.getCollection().flow();
			CollectionDataFlow<?, ?, T> multiStepFlow = sourceCL.getDef().multiStepFlow;
			if (variableMap) {
				// The refresh has to be UNDER the map
				oneStepFlow = oneStepFlow.refresh(txValue.changes().noInit());
				multiStepFlow = multiStepFlow.refresh(txValue.changes().noInit());
			}
			boolean needsUpdateReeval = !sourceCL.getDef().checkOldValues || variableMap;
			ValueHolder<TransformationPrecursor<T, X, ?>> options = new ValueHolder<>();
			boolean cache = helper.getBoolean(.75);
			boolean withReverse = transform.supportsReverse() && helper.getBoolean(.95);
			boolean fireIfUnchanged = needsUpdateReeval || helper.getBoolean();
			boolean reEvalOnUpdate = needsUpdateReeval || helper.getBoolean();
			boolean oneToMany = variableMap || transform.isOneToMany();
			boolean manyToOne = variableMap || transform.isManyToOne();
			boolean allowInexactReversible = withReverse ? helper.getBoolean(0.1) : false;
			TypeToken<X> type = (TypeToken<X>) transform.getType().getType();
			Function<T, X> map = LambdaUtils.printableFn(src -> txValue.get().map(src), () -> txValue.get().toString());
			Function<X, T> reverse = LambdaUtils.printableFn(dest -> txValue.get().reverse(dest), () -> txValue.get().reverseName());
			Consumer<TransformationPrecursor<T, X, ?>> opts = o -> {
				o.manyToOne(manyToOne).oneToMany(oneToMany);
				options.accept(o.cache(cache).fireIfUnchanged(fireIfUnchanged).reEvalOnUpdate(reEvalOnUpdate));
			};
			CollectionDataFlow<?, ?, X> derivedOneStepFlow, derivedMultiStepFlow;
			boolean mapEquivalent;
			if (oneStepFlow instanceof ObservableCollection.DistinctDataFlow && !variableMap && withReverse && !oneToMany && !manyToOne) {
				mapEquivalent = true;
				derivedOneStepFlow = ((ObservableCollection.DistinctDataFlow<?, ?, T>) oneStepFlow).transformEquivalent(//
					type, tx -> {
						opts.accept(tx);
						return tx.map(map).replaceSource(reverse, rvrs -> rvrs.allowInexactReverse(allowInexactReversible));
					});
				derivedMultiStepFlow = ((ObservableCollection.DistinctDataFlow<?, ?, T>) multiStepFlow).transformEquivalent(//
					type, tx -> {
						opts.accept(tx);
						return tx.map(map).replaceSource(reverse, rvrs -> rvrs.allowInexactReverse(allowInexactReversible));
					});
			} else {
				mapEquivalent = false;
				derivedOneStepFlow = oneStepFlow.transform(type, tx -> {
					opts.accept(tx);
					MaybeReversibleMapping<T, X> def = tx.map(map);
					if (withReverse)
						return def.replaceSource(reverse, rvrs -> rvrs.allowInexactReverse(allowInexactReversible));
					else
						return def;
				});
				derivedMultiStepFlow = multiStepFlow.transform(type, tx -> {
					opts.accept(tx);
					MaybeReversibleMapping<T, X> def = tx.map(map);
					if (withReverse)
						return def.replaceSource(reverse, rvrs -> rvrs.allowInexactReverse(allowInexactReversible));
					else
						return def;
				});
			}
			boolean checkOldValues = cache;
			if (!variableMap)
				checkOldValues |= sourceCL.getDef().checkOldValues;
			ObservableCollectionTestDef<X> newDef = new ObservableCollectionTestDef<>(transform.getType(), derivedOneStepFlow,
				derivedMultiStepFlow, sourceCL.getDef().orderImportant, checkOldValues);
			return new MappedCollectionLink<>(path, sourceCL, newDef, helper, txValue, variableMap, mapEquivalent, withReverse,
				options.get().map(map));
		}
	};

	private final SettableValue<TypeTransformation<S, T>> theMapValue;
	private TypeTransformation<S, T> theCurrentMap;
	private final boolean isMapVariable;
	private final boolean isMapEquivalent;
	private final boolean isReversible;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param def The collection definition for this link
	 * @param helper The randomness to use to initialize this link
	 * @param mapValue The value holding the current mapping
	 * @param mapVariable Whether to vary the mapping periodically
	 * @param mapEquivalent Whether the operation was a
	 *        {@link org.observe.collect.ObservableCollection.DistinctDataFlow#mapEquivalent(TypeToken, Function, Function) mapEquivalent}
	 *        operation on a distinct flow
	 * @param reversible Whether this flow supports setting values, which flow back to the parent
	 * @param options The options used to create the mapping
	 */
	public MappedCollectionLink(String path, ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper, SettableValue<TypeTransformation<S, T>> mapValue, boolean mapVariable, boolean mapEquivalent,
		boolean reversible, Transformation<S, T> options) {
		super(path, sourceLink, def, helper, options.isCached(), isInexactReversible(options));
		theMapValue = mapValue;
		theCurrentMap = theMapValue.get();
		isMapVariable = mapVariable;
		isMapEquivalent = mapEquivalent;
		isReversible = reversible;
	}

	@Override
	protected Transactable getLocking() {
		if (isMapVariable)
			return Transactable.combine(theMapValue, super.getLocking());
		else
			return super.getLocking();
	}

	@Override
	protected T map(S sourceValue) {
		return theCurrentMap.map(sourceValue);
	}

	@Override
	protected S reverse(T value) {
		if (!isReversible())
			throw new IllegalStateException();
		return theCurrentMap.reverse(value);
	}

	@Override
	protected boolean isReversible() {
		return isReversible;
	}

	@Override
	public double getModificationAffinity() {
		double affinity = super.getModificationAffinity();
		if (isMapVariable)
			affinity++;
		return affinity;
	}

	@Override
	public void tryModify(RandomAction action, TestHelper helper) {
		super.tryModify(action, helper);
		if (isMapVariable) {
			action.or(1, () -> {
				TypeTransformation<S, T> oldMap = theCurrentMap;
				TypeTransformation<S, T> newMap = MappedCollectionLink.transform(getSourceLink().getDef().type, getType(), helper, true,
					oldMap.supportsReverse());
				if (helper.isReproducing())
					System.out.println("Map " + oldMap + " -> " + newMap);
				theCurrentMap = newMap;
				theMapValue.set(newMap, null);
				expectMapChange(oldMap, newMap);
			});
		}
	}

	/**
	 * Called when the mapping is switched out
	 *
	 * @param oldMap The previously used mapping function
	 * @param newMap The new, current mapping function
	 */
	protected void expectMapChange(TypeTransformation<S, T> oldMap, TypeTransformation<S, T> newMap) {
		for (CollectionLinkElement<S, T> element : getElements()) {
			T newValue = newMap.map(element.getFirstSource().getValue());
			element.expectSet(newValue);
		}
	}

	/**
	 * @param sourceType The type to transform
	 * @param targetType The target type for the transform, or null if unspecified
	 * @param allowManyToOne Whether {@link TypeTransformation#isManyToOne() many-to-one} transformations are acceptable
	 * @param requireReversible Whether acceptable mappings must {@link TypeTransformation#supportsReverse() support reversal}
	 * @return Whether any acceptable transformations exist
	 */
	public static boolean supportsTransform(TestValueType sourceType, TestValueType targetType, boolean allowManyToOne,
		boolean requireReversible) {
		List<? extends TypeTransformation<?, ?>> transforms = TYPE_TRANSFORMATIONS.get(sourceType);
		if (transforms == null || transforms.isEmpty())
			return false;
		if (!allowManyToOne || requireReversible) {
			boolean supported = false;
			for (TypeTransformation<?, ?> transform : transforms) {
				if (!allowManyToOne && transform.isManyToOne()) {//
				} else if (requireReversible && !transform.supportsReverse()) {//
				} else if (targetType != null && transform.getType() != targetType) {//
				} else {
					supported = true;
					break;
				}
			}
			return supported;
		}
		return false;
	}

	/**
	 * @param <E> The source type
	 * @param <T> The target type
	 * @param type The source type to transform
	 * @param targetType The target type for the transform, or null if unspecified
	 * @param helper The randomness to use to get a random transformation
	 * @param allowManyToOne Whether {@link TypeTransformation#isManyToOne() many-to-one} transformations are acceptable
	 * @param requireReversible Whether acceptable mappings must {@link TypeTransformation#supportsReverse() support reversal}
	 * @return The transformation
	 */
	public static <E, T> TypeTransformation<E, T> transform(TestValueType type, TestValueType targetType, TestHelper helper,
		boolean allowManyToOne,
		boolean requireReversible) {
		List<? extends TypeTransformation<E, ?>> transforms = (List<? extends TypeTransformation<E, ?>>) TYPE_TRANSFORMATIONS.get(type)
			.stream().filter(transform -> {
				if (!allowManyToOne && transform.isManyToOne())
					return false;
				else if (requireReversible && !transform.supportsReverse())
					return false;
				else if (targetType != null && transform.getType() != targetType)
					return false;
				else
					return true;
			}).collect(Collectors.toList());
		if ((!allowManyToOne || requireReversible) && !supportsTransform(type, targetType, allowManyToOne, requireReversible))
			throw new UnsupportedOperationException();
		TypeTransformation<E, ?> transform;
		if (transforms.size() == 1)
			transform = transforms.get(0);
		else
			transform = transforms.get(helper.getInt(0, transforms.size()));
		return (TypeTransformation<E, T>) transform;
	}

	@Override
	public String toString() {
		String str = "map";
		if (isMapEquivalent)
			str += "Equivalent";
		str += "(" + theCurrentMap;
		if (isMapVariable)
			str += ", variable";
		if (!isReversible())
			str += ", irreversible";
		return str + ")";
	}

	private static <E> TypeTransformation<E, E> identity(TestValueType type) {
		return new TypeTransformation<E, E>() {
			@Override
			public TestValueType getSourceType() {
				return type;
			}

			@Override
			public TestValueType getType() {
				return type;
			}

			@Override
			public E map(E source) {
				return source;
			}

			@Override
			public boolean supportsReverse() {
				return true;
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

	private static <E, T> TypeTransformation<E, T> transform(TestValueType sourceType, TestValueType type, Function<E, T> map,
		Function<T, E> reverse, boolean manyToOne, boolean oneToMany, String name, String reverseName) {
		return new TypeTransformation<E, T>() {
			@Override
			public TestValueType getSourceType() {
				return sourceType;
			}

			@Override
			public TestValueType getType() {
				return type;
			}

			@Override
			public T map(E source) {
				return map.apply(source);
			}

			@Override
			public boolean supportsReverse() {
				return reverse != null;
			}

			@Override
			public E reverse(T mapped) {
				if (reverse == null)
					throw new UnsupportedOperationException();
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

	private static final Map<TestValueType, List<? extends TypeTransformation<?, ?>>> TYPE_TRANSFORMATIONS;
	static {
		TYPE_TRANSFORMATIONS = new HashMap<>();
		for (TestValueType type1 : TestValueType.values()) {
			for (TestValueType type2 : TestValueType.values()) {
				if (type1 == type2)
					supportTransforms(type1, type2, identity(type1));
				if (type2 == TestValueType.BOOLEAN) {
					List<Function<?, String>> filters = FilteredCollectionLink.FILTERS.get(type1);
					if (filters != null) {
						for (Function<?, String> filter : filters) {
							MappedCollectionLink.<Object, Boolean> supportTransforms(type1, type2, //
								transform(type1, type2, obj -> ((Function<Object, String>) filter).apply(obj) == null, null, true, false,
								filter.toString(), null));
						}
					}
				}
				switch (type1) {
				case INT:
					switch (type2) {
					case INT: {
						MappedCollectionLink.<Integer, Integer> supportTransforms(type1, type2, //
							transform(type1, type2, i -> i + 5, i -> i - 5, false, false, "+5", "-5"), //
							transform(type1, type2, i -> i * 5, i -> i / 5, false, true, "*5", "/5"), //
							transform(type1, type2, i -> -i, i -> -i, false, false, "-", "-"));
						break;
					}
					case DOUBLE: {
						MappedCollectionLink.<Integer, Double> supportTransforms(type1, type2, //
							transform(type1, type2, //
								i -> noNeg0(i * 1.0), //
								d -> (int) Math.round(d), //
								false, true, "*1.0", "round()"), //
							transform(type1, type2, //
								i -> noNeg0(i * 5.0), //
								d -> (int) Math.round(d / 5), //
								false, true, "*5.0", "/5,round"),
							transform(type1, type2, //
								i -> noNeg0(i / 5.0), //
								d -> (int) Math.round(d * 5), //
								true, true, "/5.0", "*5,round"));
						break;
					}
					case STRING: {
						// Although a single integer will map to a single string, there are many double strings
						// that map to the same integer, hence oneToMany=true
						MappedCollectionLink.<Integer, String> supportTransforms(type1, type2, //
							transform(type1, type2, i -> String.valueOf(i), s -> (int) Math.round(Double.parseDouble(s)), false, true,
								"toString()", "parseInt"));
						break;
					}
					case BOOLEAN:
						break;
					}
					break;
				case DOUBLE:
					switch (type2) {
					case INT: // Already done above
						break;
					case DOUBLE: {
						MappedCollectionLink.<Double, Double> supportTransforms(type1, type2, //
							// The a + b - b is not always equal to a due to precision losses
							transform(type1, type2, d -> noNeg0(d + 5), d -> noNeg0(d - 5), true, true, "+5", "-5"), //
							transform(type1, type2, d -> noNeg0(d * 5), d -> noNeg0(d / 5), false, true, "*5", "/5"), //
							transform(type1, type2, d -> noNeg0(-d), d -> noNeg0(-d), false, false, "-", "-"));
						break;
					}
					case STRING: {
						// Although a single double will map to a single string, leading zeros mean there are many strings
						// that map to the same double, hence oneToMany=true
						MappedCollectionLink.<Double, String> supportTransforms(type1, type2, //
							transform(type1, type2, d -> stringValueOf(d), s -> Double.valueOf(s), false, true, "toString()",
								"parseDouble"));
						break;
					}
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
						MappedCollectionLink.<String, String> supportTransforms(type1, type2, //
							transform(type1, type2, s -> reverse(s), s -> reverse(s), false, false, "reverse", "reverse"));
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

	private static <S, T> void supportTransforms(TestValueType type1, TestValueType type2, TypeTransformation<S, T>... transforms) {
		List<TypeTransformation<S, T>> forward = (List<TypeTransformation<S, T>>) TYPE_TRANSFORMATIONS.computeIfAbsent(type1,
			t -> new LinkedList<>());
		List<TypeTransformation<T, S>> backward = type1 == type2 ? null
			: (List<TypeTransformation<T, S>>) TYPE_TRANSFORMATIONS.computeIfAbsent(type2, t -> new LinkedList<>());
		for (TypeTransformation<S, T> transform : transforms) {
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
