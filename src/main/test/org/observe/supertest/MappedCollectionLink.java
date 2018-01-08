package org.observe.supertest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.observe.SimpleSettableValue;
import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.BiTuple;
import org.qommons.TestHelper;
import org.qommons.TestHelper.RandomAction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class MappedCollectionLink<E, T> extends AbstractObservableCollectionLink<E, T> {
	private final SimpleSettableValue<TypeTransformation<E, T>> theMapValue;
	private final boolean isMapVariable;
	private TypeTransformation<E, T> theMap;
	private final FlowOptions.MapDef<E, T> theOptions;

	public MappedCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, T> flow,
		TestHelper helper, boolean checkRemovedValues, SimpleSettableValue<TypeTransformation<E, T>> map, boolean variableMap,
		FlowOptions.MapDef<E, T> options) {
		super(parent, type, flow, helper, false, checkRemovedValues);
		theMapValue = map;
		isMapVariable = variableMap;
		theMap = map.get();
		theOptions = options;

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
				for (int i = 0; i < getParent().getExpected().size(); i++) {
					E src = getParent().getExpected().get(i);
					T newValue = newMap.map(src);
					set(i, newValue, action.getHelper(), true);
				}
			});
		}
	}

	@Override
	public void checkAddable(List<CollectionOp<T>> adds, int subListStart, int subListEnd, TestHelper helper) {
		List<CollectionOp<E>> parentAdds = new ArrayList<>(adds.size());
		for (CollectionOp<T> add : adds) {
			if (theOptions.getReverse() == null) {
				add.reject(StdMsg.UNSUPPORTED_OPERATION, true);
				continue;
			}
			E reversed = theOptions.getReverse().apply(add.source);
			if (!getCollection().equivalence().elementEquals(theMap.map(reversed), add.source)) {
				add.reject(StdMsg.ILLEGAL_ELEMENT, true);
				continue;
			}
			CollectionOp<E> parentAdd = new CollectionOp<>(add, reversed, add.index);
			parentAdds.add(parentAdd);
		}
		getParent().checkAddable(parentAdds, subListStart, subListEnd, helper);
	}

	@Override
	public void checkRemovable(List<CollectionOp<T>> removes, int subListStart, int subListEnd,
		TestHelper helper) {
		List<CollectionOp<E>> parentRemoves = new ArrayList<>(removes.size());
		for (CollectionOp<T> remove : removes) {
			if (remove.index < 0) {
				if (!getCollection().contains(remove.source)) {
					remove.reject(StdMsg.NOT_FOUND, false);
					continue;
				} else if (theOptions.getReverse() == null) {
					remove.reject(StdMsg.UNSUPPORTED_OPERATION, true);
					continue;
				}
				parentRemoves.add(new CollectionOp<>(remove, theOptions.getReverse().apply(remove.source), remove.index));
			} else
				parentRemoves.add(new CollectionOp<>(remove, getParent().getCollection().get(subListStart + remove.index), remove.index));
		}
		getParent().checkRemovable(parentRemoves, subListStart, subListEnd, helper);
	}

	@Override
	public void checkSettable(List<CollectionOp<T>> sets, int subListStart, TestHelper helper) {
		List<CollectionOp<E>> parentSets = new ArrayList<>(sets.size());
		for (CollectionOp<T> set : sets) {
			if (theOptions.getElementReverse() != null) {
				String message = theOptions.getElementReverse().setElement(getParent().getCollection().get(set.index), set.source, false);
				if (message == null)
					continue; // Don't even need to consult the parent for this
				if (theOptions.getReverse() == null) {
					set.reject(message, true);
					continue;
				}
			}
			if (theOptions.getReverse() == null) {
				set.reject(StdMsg.UNSUPPORTED_OPERATION, true);
				continue;
			}
			E reversed = theOptions.getReverse().apply(set.source);
			if (!getCollection().equivalence().elementEquals(theMap.map(reversed), set.source)) {
				set.reject(StdMsg.ILLEGAL_ELEMENT, true);
				return;
			}
			parentSets.add(new CollectionOp<>(set, reversed, set.index));
		}
		getParent().checkSettable(parentSets, subListStart, helper);
	}

	@Override
	public void addedFromBelow(List<CollectionOp<E>> adds, TestHelper helper) {
		added(adds.stream().map(add -> new CollectionOp<>(add, theMap.map(add.source), add.index)).collect(Collectors.toList()), helper,
			true);
	}

	@Override
	public void removedFromBelow(int index, TestHelper helper) {
		removed(index, helper, true);
	}

	@Override
	public void setFromBelow(int index, E value, TestHelper helper) {
		// TODO Need to cache (if options allow) to detect whether the change is an update, which may not result in an event for some
		// options
		set(index, theMap.map(value), helper, true);
	}

	@Override
	public void addedFromAbove(List<CollectionOp<T>> adds, TestHelper helper, boolean above) {
		getParent().addedFromAbove(//
			adds.stream().<CollectionOp<E>> map(add -> new CollectionOp<>(add, theOptions.getReverse().apply(add.source), add.index))
			.collect(Collectors.toList()),
			helper, true);
		added(adds, helper, !above);
	}

	@Override
	public void removedFromAbove(int index, T value, TestHelper helper, boolean above) {
		getParent().removedFromAbove(index, getParent().getExpected().get(index), helper, true);
		removed(index, helper, !above);
	}

	@Override
	public void setFromAbove(int index, T value, TestHelper helper, boolean above) {
		if (theOptions.getElementReverse() != null) {
			if (theOptions.getElementReverse().setElement(getParent().getCollection().get(index), value, true) == null)
				return;
		}
		set(index, value, helper, !above);
		getParent().setFromAbove(index, theOptions.getReverse().apply(value), helper, true);
	}

	@Override
	public String toString() {
		return "mapped(" + theMap + ")";
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
		for (int i = start; i <= c.length / 2; i++) {
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
