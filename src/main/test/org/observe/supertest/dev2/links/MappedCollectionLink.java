package org.observe.supertest.dev2.links;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.SimpleSettableValue;
import org.observe.collect.FlowOptions;
import org.observe.collect.FlowOptions.MapDef;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.OneToOneCollectionLink;
import org.observe.supertest.dev2.TestValueType;
import org.observe.supertest.dev2.TypeTransformation;
import org.qommons.BiTuple;
import org.qommons.TestHelper;

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
}
