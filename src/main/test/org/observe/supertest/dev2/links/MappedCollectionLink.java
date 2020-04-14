package org.observe.supertest.dev2.links;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.SimpleSettableValue;
import org.observe.collect.FlowOptions;
import org.observe.collect.FlowOptions.MapDef;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev2.ChainLinkGenerator;
import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.CollectionSourcedLink;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableChainLink;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.OneToOneCollectionLink;
import org.observe.supertest.dev2.TestValueType;
import org.qommons.LambdaUtils;
import org.qommons.TestHelper;
import org.qommons.TestHelper.RandomAction;
import org.qommons.Transactable;
import org.qommons.ValueHolder;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeToken;

public class MappedCollectionLink<S, T> extends OneToOneCollectionLink<S, T> {
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			if (!MappedCollectionLink.supportsTransform(sourceCL.getDef().type, true, false))
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			TypeTransformation<T, X> transform = MappedCollectionLink.transform(sourceCL.getDef().type, helper, true, false);
			SimpleSettableValue<TypeTransformation<T, X>> txValue = new SimpleSettableValue<>(
				(TypeToken<TypeTransformation<T, X>>) (TypeToken<?>) new TypeToken<Object>() {}, false);
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
			ValueHolder<FlowOptions.MapOptions<T, X>> options = new ValueHolder<>();
			boolean cache = helper.getBoolean(.75);
			boolean withReverse = transform.supportsReverse() && helper.getBoolean(.95);
			boolean fireIfUnchanged = needsUpdateReeval || helper.getBoolean();
			boolean reEvalOnUpdate = needsUpdateReeval || helper.getBoolean();
			boolean oneToMany = variableMap || transform.isOneToMany();
			boolean manyToOne = variableMap || transform.isManyToOne();
			TypeToken<X> type = (TypeToken<X>) transform.getType().getType();
			Function<T, X> map = LambdaUtils.printableFn(src -> txValue.get().map(src), () -> txValue.get().toString());
			Function<X, T> reverse = LambdaUtils.printableFn(dest -> txValue.get().reverse(dest), () -> txValue.get().reverseName());
			Consumer<FlowOptions.MapOptions<T, X>> opts = o -> {
				o.manyToOne(manyToOne).oneToMany(oneToMany);
				if (withReverse)
					o.withReverse(reverse);
				options.accept(o.cache(cache).fireIfUnchanged(fireIfUnchanged).reEvalOnUpdate(reEvalOnUpdate));
			};
			CollectionDataFlow<?, ?, X> derivedOneStepFlow, derivedMultiStepFlow;
			boolean mapEquivalent;
			if (oneStepFlow instanceof ObservableCollection.DistinctDataFlow && !variableMap && withReverse && !oneToMany && !manyToOne) {
				mapEquivalent = true;
				derivedOneStepFlow = ((ObservableCollection.DistinctDataFlow<?, ?, T>) oneStepFlow).mapEquivalent(//
					type, map, reverse, opts);
				derivedMultiStepFlow = ((ObservableCollection.DistinctDataFlow<?, ?, T>) multiStepFlow).mapEquivalent(//
					type, map, reverse, opts);
			} else {
				mapEquivalent = false;
				derivedOneStepFlow = oneStepFlow.map(type, map, opts);
				derivedMultiStepFlow = multiStepFlow.map(type, map, opts);
			}
			boolean checkOldValues = cache;
			if (!variableMap)
				checkOldValues |= sourceCL.getDef().checkOldValues;
			ObservableCollectionTestDef<X> newDef = new ObservableCollectionTestDef<>(transform.getType(), derivedOneStepFlow,
				derivedMultiStepFlow, sourceCL.getDef().orderImportant, checkOldValues);
			return new MappedCollectionLink<>(path, sourceCL, newDef, helper, txValue, variableMap, mapEquivalent,
				new FlowOptions.MapDef<>(options.get()));
		}
	};

	private final SimpleSettableValue<TypeTransformation<S, T>> theMapValue;
	private TypeTransformation<S, T> theCurrentMap;
	private final boolean isMapVariable;
	private final boolean isMapEquivalent;
	private final FlowOptions.MapDef<S, T> theOptions;

	public MappedCollectionLink(String path, ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper, SimpleSettableValue<TypeTransformation<S, T>> mapValue, boolean mapVariable, boolean mapEquivalent,
		MapDef<S, T> options) {
		super(path, sourceLink, def, helper);
		theMapValue = mapValue;
		theCurrentMap = theMapValue.get();
		this.isMapVariable = mapVariable;
		isMapEquivalent = mapEquivalent;
		theOptions = options;
	}

	@Override
	protected Transactable getSupplementalLock() {
		return isMapVariable ? theMapValue : null;
	}

	@Override
	protected T map(S sourceValue) {
		return theCurrentMap.map(sourceValue);
	}

	@Override
	protected S reverse(T value) {
		return theCurrentMap.reverse(value);
	}

	@Override
	protected boolean isReversible() {
		return theCurrentMap.supportsReverse();
	}

	@Override
	public boolean isAcceptable(T value) {
		S reversed;
		try {
			reversed = theCurrentMap.reverse(value);
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

	protected void expectMapChange(TypeTransformation<S, T> oldMap, TypeTransformation<S, T> newMap) {
		for (CollectionLinkElement<S, T> element : getElements()) {
			T oldValue = element.getValue();
			T newValue = newMap.map(element.getFirstSource().getValue());
			element.setValue(newValue);
			for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
				derived.expectFromSource(new ExpectedCollectionOperation<>(element, CollectionOpType.set, oldValue, newValue));
		}
	}

	@Override
	public CollectionLinkElement<S, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		if (theOptions.getReverse() == null) {
			rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
			return null;
		} else if (!getCollection().equivalence().elementEquals(theCurrentMap.map(theOptions.getReverse().apply(value)), value)) {
			rejection.reject(StdMsg.ILLEGAL_ELEMENT);
			return null;
		}
		return super.expectAdd(value, after, before, first, rejection);
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		switch (derivedOp.getType()) {
		case add:
		case move:
			throw new IllegalStateException();
		case remove:
			break;
		case set:
			if (theOptions.isCached()
				&& getCollection().equivalence().elementEquals(derivedOp.getElement().getValue(), derivedOp.getValue())) {
				// Update, re-use the previous source value
				CollectionLinkElement<?, S> sourceEl = (CollectionLinkElement<?, S>) derivedOp.getElement().getSourceElements().getFirst();
				S sourceValue;
				if (theOptions.getReverse() != null) {
					// If the mapping is reversible, then the source operation for an update is valid either with the reverse-mapped value
					// or the re-used previous source value. So allow either.
					S reversed = theOptions.getReverse().apply(derivedOp.getValue());
					if (getSourceLink().getCollection().equivalence().elementEquals(sourceEl.getCollectionValue(), reversed))
						sourceValue = reversed;
					else if (getSourceLink().getCollection().equivalence().elementEquals(sourceEl.getCollectionValue(),
						sourceEl.getValue()))
						sourceValue = sourceEl.getValue();
					else {
						sourceEl.error("Reverse operation produced " + sourceEl.getCollectionValue() + ", not " + reversed + " or "
							+ sourceEl.getValue());
						return;
					}
				} else
					sourceValue = sourceEl.getValue();
				getSourceLink().expect(
					new ExpectedCollectionOperation<>(sourceEl, derivedOp.getType(), sourceEl.getValue(), sourceValue), rejection,
					execute);
				return;
			}
			if (theOptions.getReverse() == null) {
				rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
				return;
			}
			S reversed = theOptions.getReverse().apply(derivedOp.getValue());
			T reMapped = theCurrentMap.map(reversed);
			if (!getCollection().equivalence().elementEquals(reMapped, derivedOp.getValue())) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT);
				return;
			}
			break;
		}
		super.expect(derivedOp, rejection, execute);
	}

	public static boolean supportsTransform(TestValueType sourceType, boolean allowManyToOne, boolean requireReversible) {
		List<? extends TypeTransformation<?, ?>> transforms = TYPE_TRANSFORMATIONS.get(sourceType);
		if (transforms == null || transforms.isEmpty())
			return false;
		if (!allowManyToOne || requireReversible) {
			boolean supported = false;
			for (TypeTransformation<?, ?> transform : transforms) {
				if (!allowManyToOne && transform.isManyToOne()) {} else if (requireReversible && !transform.supportsReverse()) {} else {
					supported = true;
					break;
				}
			}
			return supported;
		}
		return true;
	}

	public static <E, T> TypeTransformation<E, T> transform(TestValueType type, TestHelper helper, boolean allowManyToOne,
		boolean requireReversible) {
		List<? extends TypeTransformation<E, ?>> transforms = (List<? extends TypeTransformation<E, ?>>) TYPE_TRANSFORMATIONS.get(type);
		if ((!allowManyToOne || requireReversible) && !supportsTransform(type, allowManyToOne, requireReversible))
			throw new UnsupportedOperationException();
		TypeTransformation<E, ?> transform;
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
		return (TypeTransformation<E, T>) transform;
	}

	public static <E, T> TypeTransformation<E, T> transform(TestValueType sourceType, TestValueType destType, TestHelper helper,
		boolean allowManyToOne, boolean requireReversible) {
		List<? extends TypeTransformation<E, ?>> transforms = (List<? extends TypeTransformation<E, ?>>) TYPE_TRANSFORMATIONS
			.get(sourceType).stream().filter(transform -> transform.getType() == destType).filter(transform -> {
				if (!allowManyToOne && transform.isManyToOne())
					return false;
				else if (requireReversible && !transform.supportsReverse())
					return false;
				else
					return true;
			}).collect(Collectors.toList());
		TypeTransformation<E, ?> transform = transforms.get(helper.getInt(0, transforms.size()));
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
		if (theOptions.getReverse() == null)
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
