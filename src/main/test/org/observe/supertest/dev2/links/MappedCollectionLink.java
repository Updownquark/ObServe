package org.observe.supertest.dev2.links;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.observe.SimpleSettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.FlowOptions;
import org.observe.collect.FlowOptions.MapDef;
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
import org.observe.supertest.dev2.TypeTransformation;
import org.qommons.LambdaUtils;
import org.qommons.TestHelper;
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
		public <T, X> ObservableChainLink<T, X> deriveLink(ObservableChainLink<?, T> sourceLink, TestHelper helper) {
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
			boolean cache = helper.getBoolean();
			boolean withReverse = transform.supportsReverse() && helper.getBoolean(.95);
			boolean fireIfUnchanged = needsUpdateReeval || helper.getBoolean();
			boolean reEvalOnUpdate = needsUpdateReeval || helper.getBoolean();
			CollectionDataFlow<?, ?, X> derivedOneStepFlow = oneStepFlow.map((TypeToken<X>) transform.getType().getType(),
				LambdaUtils.printableFn(src -> txValue.get().map(src), () -> txValue.get().toString()), o -> {
					o.manyToOne(txValue.get().isManyToOne()).oneToMany(txValue.get().isOneToMany());
					if (withReverse)
						o.withReverse(x -> txValue.get().reverse(x));
					options.accept(o.cache(cache).fireIfUnchanged(fireIfUnchanged).reEvalOnUpdate(reEvalOnUpdate));
				});
			CollectionDataFlow<?, ?, X> derivedMultiStepFlow = multiStepFlow.map((TypeToken<X>) transform.getType().getType(),
				LambdaUtils.printableFn(src -> txValue.get().map(src), () -> txValue.get().toString()), o -> {
					o.manyToOne(txValue.get().isManyToOne()).oneToMany(txValue.get().isOneToMany());
					if (withReverse)
						o.withReverse(x -> txValue.get().reverse(x));
					options.accept(o.cache(cache).fireIfUnchanged(fireIfUnchanged).reEvalOnUpdate(reEvalOnUpdate));
				});
			ObservableCollectionTestDef<X> newDef = new ObservableCollectionTestDef<>(transform.getType(), derivedOneStepFlow,
				derivedMultiStepFlow, variableMap, !needsUpdateReeval);
			return new MappedCollectionLink<>(sourceCL, newDef, helper, txValue, variableMap, new FlowOptions.MapDef<>(options.get()));
		}
	};

	private final SimpleSettableValue<TypeTransformation<S, T>> theMapValue;
	private TypeTransformation<S, T> theCurrentMap;
	private final boolean isMapVariable;
	private final FlowOptions.MapDef<S, T> theOptions;

	public MappedCollectionLink(ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper, SimpleSettableValue<TypeTransformation<S, T>> mapValue, boolean isMapVariable, MapDef<S, T> options) {
		super(sourceLink, def, helper);
		theMapValue = mapValue;
		theCurrentMap = theMapValue.get();
		this.isMapVariable = isMapVariable;
		theOptions = options;
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
	public CollectionLinkElement<S, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, int derivedIndex) {
		if (theOptions.getReverse() == null) {
			rejection.reject(StdMsg.UNSUPPORTED_OPERATION, true);
			return null;
		} else if (!getCollection().equivalence().elementEquals(theCurrentMap.map(theOptions.getReverse().apply(value)), value)) {
			rejection.reject(StdMsg.ILLEGAL_ELEMENT, true);
			return null;
		}
		return super.expectAdd(value, after, before, first, rejection, derivedIndex);
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, int derivedIndex) {
		switch (derivedOp.getType()) {
		case add:
			throw new IllegalStateException();
		case remove:
			break;
		case set:
			if (theOptions.isCached()
				&& getCollection().equivalence().elementEquals(derivedOp.getElement().getValue(), derivedOp.getValue())) {
				// Update, re-use the previous source value
				T oldValue = derivedOp.getElement().get();
				CollectionLinkElement<?, S> sourceEl = (CollectionLinkElement<?, S>) derivedOp.getElement().getSourceElements().getFirst();
				getSourceLink().expect(
					new ExpectedCollectionOperation<>(sourceEl, derivedOp.getType(), sourceEl.getValue(), sourceEl.getValue()), rejection,
					getSiblingIndex());
				if (!rejection.isRejected()) {
					derivedOp.getElement().setValue(derivedOp.getValue());
					int d = 0;
					for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks()) {
						if (d != derivedIndex)
							derivedLink.expectFromSource(//
								new ExpectedCollectionOperation<>(derivedOp.getElement(), CollectionChangeType.set, oldValue,
									derivedOp.getValue()));
						d++;
					}
				}
				return;
			}
			if (theOptions.getReverse() == null) {
				rejection.reject(StdMsg.UNSUPPORTED_OPERATION, true);
				return;
			}
			S reversed = theOptions.getReverse().apply(derivedOp.getValue());
			T reMapped = theCurrentMap.map(reversed);
			if (!getCollection().equivalence().elementEquals(reMapped, derivedOp.getValue())) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT, true);
				return;
			}
			break;
		}
		super.expect(derivedOp, rejection, derivedIndex);
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

	@Override
	public String toString() {
		String str = "map(" + theCurrentMap;
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
