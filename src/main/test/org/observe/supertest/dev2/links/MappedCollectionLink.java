package org.observe.supertest.dev2.links;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.SimpleSettableValue;
import org.observe.collect.FlowOptions;
import org.observe.collect.FlowOptions.MapDef;
import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.OneToOneCollectionLink;
import org.observe.supertest.dev2.TestValueType;
import org.observe.supertest.dev2.TypeTransformation;
import org.qommons.BiTuple;
import org.qommons.TestHelper;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class MappedCollectionLink<S, T> extends OneToOneCollectionLink<S, T> {
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
		if (theOptions.isCached())
			return value;
		else
			return map(getSourceLink().getUpdateValue(reverse(value)));
	}

	@Override
	public CollectionLinkElement<S, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		if (theOptions.getReverse() == null) {
			rejection.reject(StdMsg.UNSUPPORTED_OPERATION, true);
			return null;
		} else if (!getCollection().equivalence().elementEquals(theCurrentMap.map(theOptions.getReverse().apply(value)), value)) {
			rejection.reject(StdMsg.ILLEGAL_ELEMENT, true);
			return null;
		}
		return super.expectAdd(value, after, before, first, rejection);
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection) {
		switch (derivedOp.getType()) {
		case add:
			throw new IllegalStateException();
		case remove:
			break;
		case set:
			if (theOptions.isCached()
				&& getCollection().equivalence().elementEquals(derivedOp.getElement().getValue(), derivedOp.getValue())) {
				// Update, re-use the previous source value
				CollectionLinkElement<?, S> sourceEl = (CollectionLinkElement<?, S>) derivedOp.getElement().getSourceElements().getFirst();
				getSourceLink().expect(
					new ExpectedCollectionOperation<>(sourceEl, derivedOp.getType(), sourceEl.getValue(), sourceEl.getValue()), rejection);
				if (!rejection.isRejected())
					derivedOp.getElement().setValue(derivedOp.getValue());
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
		super.expect(derivedOp, rejection);
	}

	public static <E, T> TypeTransformation<E, T> transform(TestValueType type1, TestValueType type2, TestHelper helper) {
		List<? extends TypeTransformation<?, ?>> transforms = TYPE_TRANSFORMATIONS.get(new BiTuple<>(type1, type2));
		return (TypeTransformation<E, T>) transforms.get(helper.getInt(0, transforms.size()));
	}

	public static <E, T> TypeTransformation<E, T> transform(TestValueType type1, TestValueType type2, TestHelper helper,
		boolean allowManyToOne) {
		List<? extends TypeTransformation<?, ?>> transforms = TYPE_TRANSFORMATIONS.get(new BiTuple<>(type1, type2));
		if (!allowManyToOne)
			transforms = transforms.stream().filter(t -> !t.isManyToOne()).collect(Collectors.toList());
		return (TypeTransformation<E, T>) transforms.get(helper.getInt(0, transforms.size()));
	}

	@Override
	public String toString() {
		String str = "map(" + theCurrentMap;
		if (isMapVariable)
			str += " variable";
		if (theOptions.getReverse() == null)
			str += " irreversible";
		return str + ")";
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
		for (int i = start; i < c.length / 2; i++) {
			char temp = c[i];
			int opposite = c.length - i - 1;
			c[i] = c[opposite];
			c[opposite] = temp;
		}
		return new String(c);
	}

	private static final Map<BiTuple<TestValueType, TestValueType>, List<? extends TypeTransformation<?, ?>>> TYPE_TRANSFORMATIONS;
	static {
		TYPE_TRANSFORMATIONS = new HashMap<>();
		for (TestValueType type1 : TestValueType.values()) {
			for (TestValueType type2 : TestValueType.values()) {
				switch (type1) {
				case INT:
					switch (type2) {
					case INT: {
						MappedCollectionLink.<Integer, Integer> supportTransforms(type1, type2, //
							identity(), //
							transform(i -> i + 5, i -> i - 5, false, false, "+5", "-5"), //
							transform(i -> i * 5, i -> i / 5, false, true, "*5", "/5"), //
							transform(i -> -i, i -> -i, false, false, "-", "-"));
						break;
					}
					case DOUBLE: {
						MappedCollectionLink.<Integer, Double> supportTransforms(type1, type2, //
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
						break;
					}
					case STRING: {
						// Although a single integer will map to a single string, there are many double strings
						// that map to the same integer, hence oneToMany=true
						MappedCollectionLink.<Integer, String> supportTransforms(type1, type2, //
							transform(i -> String.valueOf(i), s -> (int) Math.round(Double.parseDouble(s)), false, true, "toString()",
								"parseInt"));
						break;
					}
					}
					break;
				case DOUBLE:
					switch (type2) {
					case INT: // Already done above
						break;
					case DOUBLE: {
						MappedCollectionLink.<Double, Double> supportTransforms(type1, type2, //
							identity(), //
							transform(d -> d + 5, d -> d - 5, false, false, "+5", "-5"), //
							transform(d -> d * 5, d -> d / 5, false, true, "*5", "/5"), //
							transform(d -> -d, d -> -d, false, false, "-", "-"));
						break;
					}
					case STRING: {
						// Although a single double will map to a single string, leading zeros mean there are many strings
						// that map to the same double, hence oneToMany=true
						MappedCollectionLink.<Double, String> supportTransforms(type1, type2, //
							transform(d -> stringValueOf(d), s -> Double.valueOf(s), false, true, "toString()", "parseDouble"));
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
						MappedCollectionLink.<String, String> supportTransforms(type1, type2, //
							identity(), transform(s -> reverse(s), s -> reverse(s), false, false, "reverse", "reverse"));
						break;
					}
					}
				}
			}
		}
	}

	private static <S, T> void supportTransforms(TestValueType type1, TestValueType type2, TypeTransformation<S, T>... transforms) {
		BiTuple<TestValueType, TestValueType> key = new BiTuple<>(type1, type2);
		BiTuple<TestValueType, TestValueType> reverseKey = new BiTuple<>(type2, type1);
		List<TypeTransformation<S, T>> forward = ((List<TypeTransformation<S, T>>) TYPE_TRANSFORMATIONS.computeIfAbsent(key,
			k -> new LinkedList<>()));
		List<TypeTransformation<T, S>> backward = key.equals(reverseKey) ? null
			: ((List<TypeTransformation<T, S>>) TYPE_TRANSFORMATIONS.computeIfAbsent(reverseKey, k -> new LinkedList<>()));
		for (TypeTransformation<S, T> transform : transforms) {
			forward.add(transform);
			if (backward != null)
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
