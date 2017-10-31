package org.observe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionTester;
import org.qommons.BiTuple;
import org.qommons.TestHelper;
import org.qommons.TestHelper.Testable;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

public class ObservableChainTester implements Testable {
	private static enum TestValueType {
		INT(TypeToken.of(int.class)), DOUBLE(TypeToken.of(double.class)), STRING(TypeToken.of(String.class));

		private final TypeToken<?> type;

		private TestValueType(TypeToken<?> type) {
			this.type = type;
		}
	}

	private static class ModOp<E> {
		E source;
		int index;

		E result;
		String message;
		boolean isError;

		ModOp(E source, int index) {
			this.source = source;
			this.index = index;
		}
	}

	private interface ObservableChainLink {
		void tryModify(TestHelper helper);
		void check();

		ObservableChainLink derive(TestHelper helper);
	}

	private interface ObservableCollectionChainLink<E, T> extends ObservableChainLink {
		void checkAddFromAbove(ModOp<T> add);

		void checkRemoveFromAbove(ModOp<T> remove);

		void checkSetFromAbove(ModOp<T> value);

		void addedFromBelow(int index, E value);

		void removedFromBelow(int index);

		void setFromBelow(int index, E value);
	}

	private interface TypeTransformation<E, T> {
		T map(E source);

		E reverse(T mapped);

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
			};
		}
	}

	private static TestValueType nextType(TestHelper helper) {
		return TestValueType.values()[helper.getInt(0, TestValueType.values().length)];
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
		};
	}

	private static <E, T> TypeTransformation<E, T> transform(Function<E, T> map, Function<T, E> reverse) {
		return new TypeTransformation<E, T>() {
			@Override
			public T map(E source) {
				return map.apply(source);
			}

			@Override
			public E reverse(T mapped) {
				return reverse.apply(mapped);
			}
		};
	}

	private static <E, T> List<TypeTransformation<E, T>> asList(TypeTransformation<E, T>... transforms) {
		return Arrays.asList(transforms);
	}

	private static String reverse(String s) {
		char[] c = s.toCharArray();
		for (int i = 0; i <= c.length / 2; i++) {
			char temp = c[i];
			int opposite = c.length - i - 1;
			c[i] = c[opposite];
			c[opposite] = temp;
		}
		return new String(c);
	}
	private static final int MAX_VALUE = 1000;
	private static final Map<TestValueType, Function<TestHelper, ?>> SUPPLIERS;
	private static final Map<TestValueType, List<? extends Comparator<?>>> COMPARATORS;
	private static final Map<BiTuple<TestValueType, TestValueType>, List<? extends TypeTransformation<?, ?>>> TYPE_TRANSFORMATIONS;
	static {
		SUPPLIERS = new HashMap<>();
		COMPARATORS = new HashMap<>();
		for (TestValueType type : TestValueType.values()) {
			switch (type) {
			case INT:
				SUPPLIERS.put(type, helper -> helper.getInt(0, MAX_VALUE));
				COMPARATORS.put(type, Arrays.asList(Integer::compareTo, ((Comparator<Integer>) Integer::compareTo).reversed()));
				break;
			case DOUBLE:
				SUPPLIERS.put(type, helper -> helper.getDouble(0, MAX_VALUE));
				COMPARATORS.put(type, Arrays.asList(Double::compareTo, ((Comparator<Double>) Double::compareTo).reversed()));
				break;
			case STRING:
				SUPPLIERS.put(type, helper -> String.valueOf(helper.getInt(0, MAX_VALUE)));
				COMPARATORS.put(type, Arrays.asList(String::compareTo, ((Comparator<String>) String::compareTo).reversed()));
			}
		}

		TYPE_TRANSFORMATIONS = new HashMap<>();
		for (TestValueType type1 : TestValueType.values()) {
			for (TestValueType type2 : TestValueType.values()) {
				switch (type1) {
				case INT:
					switch (type2) {
					case INT: {
						List<TypeTransformation<Integer, Integer>> transforms = asList(//
							identity(), //
							transform(i -> i + 5, i -> i - 5), transform(i -> i - 5, i -> i + 5), //
							transform(i -> i * 5, i -> i / 5), transform(i -> i / 5, i -> i * 5));
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type1, type2), transforms);
						break;
					}
					case DOUBLE: {
						List<TypeTransformation<Integer, Double>> transforms = asList(//
							transform(i -> i * 1.0, d -> (int) Math.round(d)), //
							transform(i -> i * 5.0, d -> (int) Math.round(d / 5)), transform(i -> i / 5.0, d -> (int) Math.round(d * 5)));
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type1, type2), transforms);
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type2, type1),
							transforms.stream().map(t -> t.reverse()).collect(Collectors.toList()));
						break;
					}
					case STRING: {
						List<TypeTransformation<Integer, String>> transforms = asList(//
							transform(i -> String.valueOf(i), s -> Integer.valueOf(s)));
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
							transform(d -> d + 5, d -> d - 5), transform(d -> d - 5, d -> d + 5), //
							transform(d -> d * 5, d -> d / 5), transform(d -> d / 5, d -> d * 5));
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type1, type2), transforms);
						break;
					}
					case STRING: {
						List<TypeTransformation<Double, String>> transforms = asList(//
							transform(d -> String.valueOf(d), s -> Double.valueOf(s)));
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
							identity(), transform(s -> reverse(s), s -> reverse(s)));
						TYPE_TRANSFORMATIONS.put(new BiTuple<>(type1, type2), transforms);
						break;
					}
					}
				}
			}
		}
	}

	private static <E> Comparator<E> randomComparator(TestValueType type, TestHelper helper) {
		List<Comparator<E>> typeCompares = (List<Comparator<E>>) COMPARATORS.get(type);
		return typeCompares.get(helper.getInt(0, typeCompares.size()));
	}
	private static final int MAX_CHAIN_LENGTH = 5;

	private final List<ObservableChainLink> theChain = new ArrayList<>();

	@Override
	public void accept(TestHelper helper) {
		assemble(helper);
		test(helper);
	}

	private <E> void assemble(TestHelper helper) {
		//Tend toward smaller chain lengths, but allow longer ones occasionally
		int chainLength = helper.getInt(2, helper.getInt(2, MAX_CHAIN_LENGTH));
		ObservableChainLink initLink = createInitialLink(helper);
		theChain.add(initLink);
		while (theChain.size() < chainLength)
			theChain.add(theChain.get(theChain.size() - 1).derive(helper));
	}

	private <E> ObservableChainLink createInitialLink(TestHelper helper) {
		int linkTypes = 3;
		switch (helper.getInt(0, linkTypes)) {
		case 0:
			// TODO Uncomment this when CircularArrayList is working
			// return new ObservableCollectionLinkTester<>(null, new DefaultObservableCollection<>((TypeToken<E>) type.type,
			// CircularArrayList.build().build()));
		case 1:
			return new ObservableCollectionLinkTester<>(null,
				new DefaultObservableCollection<>((TypeToken<E>) type.type, new BetterTreeList<>(true)));
		case 2:
			return new ObservableCollectionLinkTester<>(null,
				new DefaultObservableCollection<>((TypeToken<E>) type.type, new BetterTreeSet<>(true, randomComparator(type, helper))));
			// TODO ObservableValue
			// TODO ObservableMultiMap
			// TODO ObservableMap
			// TODO ObservableTree?
			// TODO ObservableGraph?
		}
		throw new IllegalStateException();
	}

	private static abstract class AbstractObservableCollectionLink<E> implements ObservableCollectionChainLink<E, E> {
		private final ObservableCollectionChainLink<?, E> theParent;
		private final ObservableCollection<E> theCollection;
		private final ObservableCollectionTester<E> theTester;
		private ObservableCollectionChainLink<E, ?> theChild;
		private final Function<TestHelper, E> theSupplier;

		AbstractObservableCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type,
			ObservableCollection<E> collection) {
			theParent = parent;
			theCollection = collection;
			theTester = new ObservableCollectionTester<>(collection);
			theSupplier = (Function<TestHelper, E>) SUPPLIERS.get(type);
		}

		@Override
		public void tryModify(TestHelper helper) {
			ModOp<E> op;
			List<ModOp<E>> ops;
			int subListStart;
			List<E> modify;
			if (helper.getBoolean(.05)) {
				subListStart = helper.getInt(0, theCollection.size());
				modify = theCollection.subList(subListStart, subListStart + helper.getInt(0, theCollection.size() - subListStart));
			} else {
				subListStart = 0;
				modify = theCollection;
			}
			switch (helper.getInt(0, 15)) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4: // More position-less adds than other ops
				op = new ModOp<>(theSupplier.apply(helper), -1);
				checkAddFromAbove(op);
				addToCollection(op, helper);
				break;
			case 5: // Add by index
				op = new ModOp<>(theSupplier.apply(helper), subListStart + (modify.isEmpty() ? -1 : helper.getInt(0, modify.size() + 1)));
				checkAddFromAbove(op);
				addToCollection(op, helper);
				break;
			case 6: // addAll
				int length = helper.getInt(0, helper.getInt(0, helper.getInt(0, 1000))); // Aggressively tend smaller
				ops = new ArrayList<>(length);
				for (int i = 0; i < length; i++)
					ops.add(new ModOp<>(theSupplier.apply(helper), -1));
				int index = subListStart + ((theCollection.isEmpty() || helper.getBoolean()) ? -1 : helper.getInt(0, modify.size() + 1));
				checkAddAll(index, ops);
				addAllToCollection(index, ops);
				break;
			case 7:
			case 8: // Set
				if (theCollection.isEmpty())
					return;
				op = new ModOp<>(theSupplier.apply(helper), helper.getInt(0, modify.size()));
				checkSetFromAbove(op);
				setInCollection(op);
				break;
			case 9:
				op = new ModOp<>(theSupplier.apply(helper), -1);
				checkRemoveFromAbove(op);
				removeFromCollection(op);
				break;
			case 10:
				if (theCollection.isEmpty())
					return;
				op = new ModOp<>(null, helper.getInt(0, modify.size()));
				checkRemoveFromAbove(op);
				removeFromCollection(op);
				break;
			case 11: // removeAll
				length = helper.getInt(0, helper.getInt(0, helper.getInt(0, 1000))); // Aggressively tend smaller
				ops = new ArrayList<>(length);
				for (int i = 0; i < length; i++)
					ops.add(new ModOp<>(theSupplier.apply(helper), -1));
				checkRemoveAll(ops);
				removeAllFromCollection(ops);
				break;
			case 12: // retainAll
				length = helper.getInt(0, 1000);
				ops = new ArrayList<>(length);
				for (int i = 0; i < length; i++)
					ops.add(new ModOp<>(theSupplier.apply(helper), -1));
				checkRetainAll(ops);
				retainAllFromCollection(ops);
				break;
			case 13:
				testBounds(helper);
				break;
				// TODO
			}
			// TODO Auto-generated method stub

		}

		private void addToCollection(ModOp<E> add, TestHelper helper) {
			int preSize = theCollection.size();
			add.message = theCollection.canAdd(add.source);
			if (add.index < 0) {
				if (add.message != null) {
					try {
						Assert.assertFalse(theCollection.add(add.source));
						add.isError = false;
					} catch (UnsupportedOperationException | IllegalArgumentException e) {
						add.isError = true;
						// Don't test this.
						// As long as the message's presence correctly predicts the exception, it's ok for the messages to be different.
						// Assert.assertEquals(add.message, e.getMessage());
					}
					Assert.assertEquals(preSize, theCollection.size());
				} else {
					CollectionElement<E> element = theCollection.addElement(add.source, helper.getBoolean());
					Assert.assertNotNull(element);
					Assert.assertEquals(preSize + 1, theCollection.size());
					Assert.assertTrue(theCollection.equivalence().elementEquals(element.get(), add.source));
					add.result = element.get();
					add.index = theCollection.getElementsBefore(element.getElementId());
					Assert.assertTrue(add.index >= 0 && add.index <= preSize);
				}
			} else {
				if (theCollection.isEmpty() || helper.getBoolean()) {
					// Test simple add by index
					try {
						CollectionElement<E> element = theCollection.addElement(add.index, add.source);
						if (element == null) {
							Assert.assertEquals(preSize, theCollection.size());
							add.message = "";
							return;
						}
						Assert.assertEquals(preSize + 1, theCollection.size());
						add.index = theCollection.getElementsBefore(element.getElementId());
						Assert.assertTrue(add.index >= 0 && add.index <= preSize);
					} catch (UnsupportedOperationException | IllegalArgumentException e) {
						add.isError = true;
						add.message = e.getMessage();
						return;
					}
				} else {
					// Test add by element
					boolean addLeft;
					if (add.index == 0)
						addLeft = true;
					else if (add.index == theCollection.size())
						addLeft = false;
					else
						addLeft = helper.getBoolean();
					MutableCollectionElement<E> element = theCollection
						.mutableElement(theCollection.getElement(addLeft ? add.index : add.index - 1).getElementId());
					add.message = element.canAdd(add.source, addLeft);
					if (add.message != null) {
						try {
							Assert.assertNull(element.add(add.source, addLeft));
							add.isError = false;
						} catch (UnsupportedOperationException | IllegalArgumentException e) {
							add.isError = true;
							// Don't test this.
							// As long as the message's presence correctly predicts the exception, it's ok for the messages to be different.
							// Assert.assertEquals(add.message, e.getMessage());
						}
						Assert.assertEquals(preSize, theCollection.size());
					} else {
						ElementId newElement = element.add(add.source, addLeft);
						Assert.assertNotNull(newElement);
						Assert.assertEquals(preSize + 1, theCollection.size());
						add.result = element.get();
						Assert
						.assertTrue(theCollection.equivalence().elementEquals(theCollection.getElement(newElement).get(), add.source));
						add.index = theCollection.getElementsBefore(newElement);
						Assert.assertTrue(add.index >= 0 && add.index <= preSize);
					}
				}
			}
			if (add.message == null)
				add.result = add.source; // No mapping if we're the root
		}

		private E removeFromCollection(int index) {}

		private E setInCollection(int index, E value) {}

		private void testBounds(TestHelper helper) {
			try {
				theCollection.get(-1);
				Assert.assertFalse("Should have errored", true);
			} catch (IndexOutOfBoundsException e) {}
			try {
				theCollection.get(theCollection.size());
				Assert.assertFalse("Should have errored", true);
			} catch (IndexOutOfBoundsException e) {}
			try {
				theCollection.remove(-1);
				Assert.assertFalse("Should have errored", true);
			} catch (IndexOutOfBoundsException e) {}
			try {
				theCollection.remove(theCollection.size());
				Assert.assertFalse("Should have errored", true);
			} catch (IndexOutOfBoundsException e) {}
			try {
				theCollection.add(-1, theSupplier.apply(helper));
				Assert.assertFalse("Should have errored", true);
			} catch (IndexOutOfBoundsException e) {}
			try {
				theCollection.add(theCollection.size() + 1, theSupplier.apply(helper));
				Assert.assertFalse("Should have errored", true);
			} catch (IndexOutOfBoundsException e) {}
			try {
				theCollection.set(-1, theSupplier.apply(helper));
				Assert.assertFalse("Should have errored", true);
			} catch (IndexOutOfBoundsException e) {}
			try {
				theCollection.set(theCollection.size() + 1, theSupplier.apply(helper));
				Assert.assertFalse("Should have errored", true);
			} catch (IndexOutOfBoundsException e) {}
		}

		@Override
		public void addedFromBelow(int index, E value) {
			theTester.add(index, value);
			if (theChild != null)
				theChild.addedFromBelow(index, value);
		}

		@Override
		public void removedFromBelow(int index) {
			theTester.getExpected().remove(index);
			if (theChild != null)
				theChild.removedFromBelow(index);
		}

		@Override
		public void setFromBelow(int index, E value) {
			theTester.getExpected().set(index, value);
			if (theChild != null)
				theChild.setFromBelow(index, value);
		}

		@Override
		public ObservableChainLink derive(TestHelper helper) {
			return deriveFromFlow(this, theCollection.flow(), type, assertModifiable, helper);
		}

		@Override
		public void check() {
			theTester.check();
		}
	}

	private static <E, X> ObservableCollectionChainLink<E, X> deriveFromFlow(ObservableCollectionChainLink<?, E> parent,
		CollectionDataFlow<?, ?, E> flow, TestValueType type, boolean assertModifiable, TestHelper helper) {
		// TODO Auto-generated method stub
	}
}
