package org.observe.supertest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.SimpleSettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.BiTuple;
import org.qommons.TestHelper;
import org.qommons.TestHelper.RandomAction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class MappedCollectionLink<E, T> extends OneToOneCollectionLink<E, T> {
	private final SimpleSettableValue<TypeTransformation<E, T>> theMapValue;
	private final boolean isMapVariable;
	private TypeTransformation<E, T> theMap;
	private final FlowOptions.MapDef<E, T> theOptions;

	public MappedCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, T> flow,
		TestHelper helper, boolean checkRemovedValues, SimpleSettableValue<TypeTransformation<E, T>> map, boolean variableMap,
		FlowOptions.MapDef<E, T> options) {
		super(parent, type, flow, helper, checkRemovedValues);
		theMapValue = map;
		isMapVariable = variableMap;
		theMap = map.get();
		theOptions = options;
	}

	@Override
	public void initialize(TestHelper helper) {
		super.initialize(helper);
		for (E src : getParent().getCollection())
			getExpected().add(theMap.map(src));
	}

	@Override
	protected void addExtraActions(RandomAction action) {
		super.addExtraActions(action);
		if(isMapVariable){
			action.or(1, () -> {
				TypeTransformation<E, T> newMap = transform(getParent().getTestType(), getTestType(), action.getHelper(),
					theOptions.isManyToOne());
				TypeTransformation<E, T> oldMap = theMap;
				if (action.getHelper().isReproducing())
					System.out.println("Map change from " + oldMap + " to " + newMap);
				theMap = newMap;
				theMapValue.set(newMap, null);
				List<CollectionOp<T>> sets = new ArrayList<>();
				for (int i = 0; i < getParent().getExpected().size(); i++) {
					E src = getParent().getExpected().get(i);
					T newValue = newMap.map(src);
					sets.add(new CollectionOp<>(CollectionChangeType.set, getElements().get(i), i, newValue));
				}
				modified(sets, action.getHelper(), true);
			});
		}
	}

	@Override
	public void checkModifiable(List<CollectionOp<T>> ops, int subListStart, int subListEnd, TestHelper helper) {
		List<CollectionOp<E>> parentOps = new ArrayList<>(ops.size());
		for (CollectionOp<T> op : ops) {
			switch (op.type) {
			case add:
				if (theOptions.getReverse() == null) {
					op.reject(StdMsg.UNSUPPORTED_OPERATION, true);
					continue;
				}
				E reversed = theOptions.getReverse().apply(op.value);
				if (!getCollection().equivalence().elementEquals(theMap.map(reversed), op.value)) {
					op.reject(StdMsg.ILLEGAL_ELEMENT, true);
					continue;
				}
				parentOps.add(new CollectionOp<>(op, op.type, op.index, reversed));
				break;
			case remove:
				if (op.index < 0) {
					if (!getCollection().contains(op.value)) {
						op.reject(StdMsg.NOT_FOUND, false);
						continue;
					} else if (theOptions.getReverse() == null) {
						op.reject(StdMsg.UNSUPPORTED_OPERATION, true);
						continue;
					}
					parentOps.add(new CollectionOp<>(op, op.type, -1, theOptions.getReverse().apply(op.value)));
				} else
					parentOps.add(
						new CollectionOp<>(op, op.type, op.index, getParent().getCollection().get(subListStart + op.index)));
				break;
			case set:
				if (theOptions.getElementReverse() != null) {
					String message = theOptions.getElementReverse().setElement(getParent().getCollection().get(subListStart + op.index),
						op.value, false);
					if (message == null)
						continue; // Don't even need to consult the parent for this
					if (theOptions.getReverse() == null) {
						op.reject(message, true);
						continue;
					}
				}
				if (theOptions.getReverse() == null) {
					op.reject(StdMsg.UNSUPPORTED_OPERATION, true);
					continue;
				}
				reversed = theOptions.getReverse().apply(op.value);
				if (!getCollection().equivalence().elementEquals(theMap.map(reversed), op.value)) {
					op.reject(StdMsg.ILLEGAL_ELEMENT, true);
					return;
				}
				parentOps.add(new CollectionOp<>(op, op.type, op.index, reversed));
				break;
			}
		}
		getParent().checkModifiable(parentOps, subListStart, subListEnd, helper);
	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		List<CollectionOp<T>> mappedOps = ops.stream()
			.map(op -> new CollectionOp<>(op.type, getDestElement(op.elementId), op.index, theMap.map(op.value)))
			.collect(Collectors.toList());
		modified(mappedOps, helper, true);
	}

	@Override
	public void fromAbove(List<CollectionOp<T>> ops, TestHelper helper, boolean above) {
		List<CollectionOp<E>> parentOps = ops.stream()
			.map(op -> new CollectionOp<>(op.type, getSourceElement(op.elementId), op.index, theMap.reverse(op.value)))
			.collect(Collectors.toList());
		getParent().fromAbove(parentOps, helper, true);
		modified(ops, helper, !above);
	}

	@Override
	public String toString() {
		String s = "mapped(" + theMap;
		if (isMapVariable)
			s += ", variable";
		s += getExtras();
		s += ")";
		return s;
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
