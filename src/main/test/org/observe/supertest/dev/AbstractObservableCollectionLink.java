package org.observe.supertest.dev;

import static org.observe.collect.CollectionChangeType.add;
import static org.observe.collect.CollectionChangeType.remove;
import static org.observe.collect.CollectionChangeType.set;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.observe.Observable;
import org.observe.SimpleSettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.Equivalence;
import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl;
import org.observe.collect.ObservableCollectionTester;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableElementTester;
import org.observe.collect.ObservableSortedSet;
import org.observe.supertest.dev.MappedCollectionLink.TypeTransformation;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.qommons.BiTuple;
import org.qommons.Ternian;
import org.qommons.TestHelper;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

abstract class AbstractObservableCollectionLink<E, T> implements ObservableCollectionChainLink<E, T> {
	private final ObservableCollectionChainLink<?, E> theParent;
	private final TestValueType theType;
	private final CollectionDataFlow<?, ?, T> theFlow;
	private final ObservableCollection<T> theCollection;
	private final ObservableCollectionTester<T> theTester;
	private ObservableCollectionChainLink<T, ?> theChild;
	private final Function<TestHelper, T> theSupplier;

	private final BetterList<BiTuple<Integer, T>> theNewValues;

	// Extras
	private ObservableElement<T> theMonitoredElement;
	private Supplier<CollectionElement<T>> theCorrectMonitoredElement;
	private ObservableElementTester<T> theMonitoredElementTester;

	private String theExtras;

	AbstractObservableCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type,
		CollectionDataFlow<?, ?, T> flow, TestHelper helper, boolean rebasedFlowRequired, boolean checkRemovedValues) {
		theParent = parent;
		theType = type;
		boolean passive = flow.supportsPassive() && helper.getBoolean();
		if (passive)
			theCollection = flow.collectPassive();
		else
			theCollection = flow.collectActive(Observable.empty);
		theSupplier = (Function<TestHelper, T>) ObservableChainTester.SUPPLIERS.get(type);
		if (parent == null) {
			// Populate the base collection with initial values.
			int length = (int) helper.getDouble(0, 100, 1000); // Aggressively tend smaller
			List<T> values = new ArrayList<>(length);
			for (int i = 0; i < length; i++)
				values.add(theSupplier.apply(helper));
			// We're not testing add or addAll here, but just initial value handling in the listeners
			// We're also not concerned with whether any of the values are illegal or duplicates.
			// The addAll method should not throw exceptions
			theCollection.addAll(values);
		}
		if (!rebasedFlowRequired && helper.getBoolean())
			theFlow = flow;
		else
			theFlow = theCollection.flow();
		theTester = new ObservableCollectionTester<>("Link " + getLinkIndex(), theCollection);
		theTester.checkRemovedValues(checkRemovedValues);
		theTester.getExpected().clear();

		theNewValues = new BetterTreeList<>(false);
		getCollection().onChange(evt -> {
			switch (evt.getType()) {
			case add:
				theNewValues.add(new BiTuple<>(evt.getIndex(), evt.getNewValue()));
				break;
			default:
			}
		});

		// Extras
		theExtras = "";
		// TODO This might be better as another link in the chain when value testing is implemented
		TestHelper.RandomAction extraAction = helper.doAction(10, () -> {
			// Usually, do nothing
		}).or(1, () -> {
			// observeElement
			T value = theSupplier.apply(helper);
			boolean first = helper.getBoolean();
			/*theExtras = ", equivalent(" + value + ", " + (first ? "first" : "last") + ")";
			theMonitoredElement = theCollection.observeElement(value, first);
			theCorrectMonitoredElement = () -> {
				ValueHolder<CollectionElement<T>> el = new ValueHolder<>();
				ElementSpliterator<T> spliter = theCollection.spliterator();
				while (el.get() == null && spliter.forElement(e -> {
					if (theCollection.equivalence().elementEquals(e.get(), value))
						el.accept(e);
				}, true)) {}
				return el.get();
			};*/
		}).or(1, () -> {
			// min/max
			Comparator<T> compare = SortedCollectionLink.compare(type, helper);
			boolean min = helper.getBoolean();
			boolean first = helper.getBoolean();
			if (min || !first)
				return;
			theExtras = ", " + (min ? "min" : "max") + "(" + (first ? "first" : "last") + ")";
			theMonitoredElement = min ? theCollection.minBy(compare, () -> null, Ternian.of(first))
				: theCollection.maxBy(compare, () -> null, Ternian.of(first));
			theCorrectMonitoredElement = () -> {
				ValueHolder<CollectionElement<T>> el = new ValueHolder<>();
				ElementSpliterator<T> spliter = theCollection.spliterator();
				while (spliter.forElement(e -> {
					boolean better = el.get() == null;
					if (!better) {
						int comp = compare.compare(e.get(), el.get().get());
						if (comp == 0)
							better = !first;
						else if ((comp < 0) == min)
							better = true;
					}
					if (better) {
						el.accept(e);
						return;
					}
				}, true)) {}
				return el.get();
			};
		}).or(1, () -> {
			// First/last
			boolean first = helper.getBoolean();
			/*theExtras = ", " + (first ? "first" : "last");
			theMonitoredElement = theCollection.observeFind(v -> true, () -> null, first);
			theCorrectMonitoredElement = () -> theCollection.getTerminalElement(first);*/
		});
		extraAction.execute(null);
		if (theMonitoredElement != null)
			theMonitoredElementTester = new ObservableElementTester<>(theMonitoredElement);
	}

	@Override
	public ObservableCollectionChainLink<?, E> getParent() {
		return theParent;
	}

	@Override
	public ObservableCollectionChainLink<T, ?> getChild() {
		return theChild;
	}

	@Override
	public ObservableCollection<T> getCollection() {
		return theCollection;
	}

	@Override
	public List<T> getExpected() {
		return theTester.getExpected();
	}

	@Override
	public String printValue() {
		return theCollection.size() + theCollection.toString();
	}

	@Override
	public TestValueType getTestType() {
		return theType;
	}

	@Override
	public TypeToken<T> getType() {
		return (TypeToken<T>) theType.getType();
	}

	BiTuple<Integer, T> getNextAddition() {
		return theNewValues.pollFirst();
	}

	protected String getExtras() {
		return theExtras;
	}

	@Override
	public Transaction lock() {
		return theCollection.lock(true, null);
	}

	@Override
	public void tryModify(TestHelper helper) {
		int subListStart, subListEnd;
		BetterList<T> modify;
		if (helper.getBoolean(.05)) {
			subListStart = helper.getInt(0, theCollection.size());
			subListEnd = subListStart + helper.getInt(0, theCollection.size() - subListStart);
			modify = theCollection.subList(subListStart, subListEnd);
			if (helper.isReproducing())
				System.out.println("subList(" + subListStart + ", " + subListEnd + ")");
		} else {
			subListStart = 0;
			subListEnd = theCollection.size();
			modify = theCollection;
		}
		TestHelper.RandomAction action=helper.doAction(5, () -> { // More position-less adds than other ops
			CollectionOp<T> op = new CollectionOp<>(null, add, theSupplier.apply(helper), -1);
			if (helper.isReproducing())
				System.out.println(op);
			checkModifiable(Arrays.asList(op), subListStart, subListEnd, helper);
			int index = addToCollection(op, modify, helper);
			if (op.getMessage() == null) {
				op = new CollectionOp<>(null, add, op.value, index);
				postModify(Arrays.asList(op), subListStart, helper);
			}
		}).or(1, () -> { // Add by index
			CollectionOp<T> op = new CollectionOp<>(null, add, theSupplier.apply(helper), helper.getInt(0, modify.size() + 1));
			if (helper.isReproducing())
				System.out.println(op);
			List<CollectionOp<T>> ops = Arrays.asList(op);
			checkModifiable(ops, subListStart, subListEnd, helper);
			addToCollection(op, modify, helper);
			if (op.getMessage() == null)
				postModify(ops, subListStart, helper);
		}).or(1, () -> { // addAll
			int length = (int) helper.getDouble(0, 100, 1000); // Aggressively tend smaller
			int index = helper.getBoolean() ? -1 : helper.getInt(0, modify.size() + 1);
			List<CollectionOp<T>> ops = new ArrayList<>(length);
			for (int i = 0; i < length; i++) {
				CollectionOp<T> op = new CollectionOp<>(null, add, theSupplier.apply(helper), index);
				ops.add(op);
			}
			if (helper.isReproducing()) {
				String msg = "Add all ";
				if (index >= 0) {
					msg += "@" + index;
					if (index > 0)
						msg += ", after " + modify.get(index - 1);
					msg += " ";
				}
				System.out.println(msg + ops.size() + ops.stream().map(op -> op.value).collect(Collectors.toList()));
			}
			checkModifiable(ops, subListStart, subListEnd, helper);
			addAllToCollection(index, ops, modify, subListStart, subListEnd, helper);
			if (!ops.isEmpty())
				fromAbove(ops, helper, false); // The addAllToCollection does the filtering and subListStart addition
		}).or(2, () -> { // Set
			if (modify.isEmpty()) {
				if (helper.isReproducing())
					System.out.println("Set, but empty");
				return;
			}
			CollectionOp<T> op = new CollectionOp<>(null, set, theSupplier.apply(helper), helper.getInt(0, modify.size()));
			if (helper.isReproducing())
				System.out.println("Set @" + op.index + " " + modify.get(op.index) + "->" + op.value);
			checkModifiable(Arrays.asList(op), subListStart, subListEnd, helper);
			setInCollection(op, modify, helper);
			if (op.getMessage() == null)
				postModify(Arrays.asList(op), subListStart, helper);
		}).or(1, () -> {// Remove by value
			T value = theSupplier.apply(helper);
			if (helper.isReproducing())
				System.out.println("Remove " + value);
			CollectionOp<T> op = null;
			for (int i = 0; i < modify.size(); i++) {
				if (theCollection.equivalence().elementEquals(modify.get(i), value)) {
					if (helper.isReproducing())
						System.out.println("\t\tIndex " + i);
					op = new CollectionOp<>(null, remove, value, i);
					checkModifiable(Arrays.asList(op), subListStart, subListEnd, helper);
					break;
				}
			}
			removeFromCollection(value, op, modify, helper);
			if (op != null && op.getMessage() == null)
				postModify(Arrays.asList(op), subListStart, helper);
		}).or(1, () -> {// Remove by index
			if (modify.isEmpty()) {
				if (helper.isReproducing())
					System.out.println("Remove, but empty");
				return;
			}
			int index = helper.getInt(0, modify.size());
			CollectionOp<T> op = new CollectionOp<>(null, remove, modify.get(index), index);
			if (helper.isReproducing())
				System.out.println(op);
			checkModifiable(Arrays.asList(op), subListStart, subListEnd, helper);
			removeFromCollection(op, modify, helper);
			if (op.getMessage() == null)
				postModify(Arrays.asList(op), subListStart, helper);
		}).or(1, () -> { // removeAll
			int length = (int) helper.getDouble(0, 250, 1000); // Tend smaller
			List<T> values = new ArrayList<>(length);
			BetterSet<T> valueSet = theCollection.equivalence().createSet();
			for (int i = 0; i < length; i++) {
				T value = theSupplier.apply(helper);
				values.add(value);
				valueSet.add(value);
			}
			if (helper.isReproducing())
				System.out.println("Remove all " + values.size() + values);
			List<CollectionOp<T>> ops = new ArrayList<>(length);
			for (int i = 0; i < modify.size(); i++) {
				T value = modify.get(i);
				if (valueSet.contains(value)) {
					CollectionOp<T> op = new CollectionOp<>(null, remove, value, i);
					ops.add(op);
				}
			}
			checkModifiable(ops, subListStart, subListEnd, helper);
			if (helper.isReproducing())
				System.out.println("\tShould remove " + ops.size() + " " + CollectionOp.print(ops));
			removeAllFromCollection(values, ops, modify, helper);
			Collections.reverse(ops); // Indices need to be descending
			postModify(ops, subListStart, helper);
		}).or(1, () -> { // retainAll
			// Allow for larger, because the smaller the generated collection,
			// the more elements will be removed from the collection
			int length = helper.getInt(0, 5000);
			List<T> values = new ArrayList<>(length);
			Set<T> valueSet = theCollection.equivalence().createSet();
			for (int i = 0; i < length; i++) {
				T value = theSupplier.apply(helper);
				values.add(value);
				valueSet.add(value);
			}
			if (helper.isReproducing())
				System.out.println("Retain all " + values.size() + values);
			List<CollectionOp<T>> ops = new ArrayList<>();
			for (int i = 0; i < modify.size(); i++) {
				T value = modify.get(i);
				if (!valueSet.contains(value)) {
					CollectionOp<T> op = new CollectionOp<>(null, remove, value, i);
					ops.add(op);
				}
			}
			checkModifiable(ops, subListStart, subListEnd, helper);
			if (helper.isReproducing())
				System.out.println("\tShould remove " + ops.size() + " " + CollectionOp.print(ops));
			retainAllInCollection(values, ops, modify, helper);
			Collections.reverse(ops); // Indices need to be descending
			postModify(ops, subListStart, helper);
		}).or(1, () -> {
			if (helper.isReproducing())
				System.out.println("[" + getLinkIndex() + "]: Check bounds");
			testBounds(helper);
		});
		addExtraActions(action);
		action.execute("Modification");
	}

	protected void addExtraActions(TestHelper.RandomAction action) {}

	protected int getLinkIndex() {
		ObservableChainLink<?> link = getParent();
		int index = 0;
		while (link != null) {
			index++;
			link = link.getParent();
		}
		return index;
	}

	private int addToCollection(CollectionOp<T> add, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		if (add.index < 0) {
			if (add.getMessage() != null)
				Assert.assertNotNull(modify.canAdd(add.value));
			else
				Assert.assertNull(modify.canAdd(add.value));
			// Test simple add value
			CollectionElement<T> element;
			boolean first = helper.getBoolean();
			if (helper.isReproducing())
				System.out.println("\t\tfirst=" + first);
			try {
				element = modify.addElement(add.value, first);
			} catch (UnsupportedOperationException | IllegalArgumentException e) {
				element = null;
			}
			if (element != null) {
				Assert.assertTrue(element.getElementId().isPresent());
				Assert.assertNull(add.getMessage());
				Assert.assertTrue(theCollection.equivalence().elementEquals(add.value, element.get()));
				Assert.assertEquals(preModSize + 1, modify.size());
				Assert.assertEquals(preSize + 1, theCollection.size());
				return modify.getElementsBefore(element.getElementId());
			} else {
				Assert.assertNotNull(add.getMessage());
				Assert.assertEquals(preModSize, modify.size());
				Assert.assertEquals(preSize, theCollection.size());
				return -1;
			}
		} else {
			if (modify.isEmpty() || helper.getBoolean()) {
				// Test simple add by index
				try {
					CollectionElement<T> element = modify.addElement(add.index, add.value);
					if (element == null) {
						Assert.assertNotNull(add.getMessage());
						Assert.assertEquals(preModSize, modify.size());
						return -1;
					} else {
						Assert.assertNull(add.getMessage());
						Assert.assertTrue(theCollection.equivalence().elementEquals(add.value, element.get()));
					}
					Assert.assertEquals(preModSize + 1, modify.size());
					Assert.assertEquals(preSize + 1, theCollection.size());
					int index = modify.getElementsBefore(element.getElementId());
					Assert.assertTrue(index >= 0 && index <= preModSize);
					return index;
				} catch (UnsupportedOperationException | IllegalArgumentException e) {
					Assert.assertNotNull(add.getMessage());
					return -1;
				}
			} else {
				// Test add by element
				boolean addLeft;
				if (add.index == 0)
					addLeft = true;
				else if (add.index == modify.size())
					addLeft = false;
				else
					addLeft = helper.getBoolean();
				MutableCollectionElement<T> element = modify
					.mutableElement(modify.getElement(addLeft ? add.index : add.index - 1).getElementId());
				if (add.getMessage() != null)
					Assert.assertNotNull(element.canAdd(add.value, addLeft));
				else
					Assert.assertNull(element.canAdd(add.value, addLeft));
				if (add.getMessage() != null) {
					try {
						Assert.assertNull(element.add(add.value, addLeft));
						Assert.assertFalse(add.isError());
					} catch (UnsupportedOperationException | IllegalArgumentException e) {
						// Don't test this.
						// As long as the message's presence correctly predicts the exception, it's ok for the messages to be different.
						// Assert.assertEquals(add.message, e.getMessage());
					}
					Assert.assertEquals(preModSize, modify.size());
					Assert.assertEquals(preSize, theCollection.size());
					return -1;
				} else {
					ElementId newElement = element.add(add.value, addLeft);
					Assert.assertNotNull(newElement);
					Assert.assertEquals(preModSize + 1, modify.size());
					Assert.assertEquals(preSize + 1, theCollection.size());
					Assert.assertTrue(theCollection.equivalence().elementEquals(modify.getElement(newElement).get(), add.value));
					int index = modify.getElementsBefore(newElement);
					Assert.assertTrue(index >= 0 && index <= preModSize);
					return index;
				}
			}
		}
	}

	private void removeFromCollection(CollectionOp<T> op, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		MutableCollectionElement<T> element = modify.mutableElement(modify.getElement(op.index).getElementId());
		if (op.getMessage() != null)
			Assert.assertNotNull(element.canRemove());
		else
			Assert.assertNull(element.canRemove());
		if (helper.getBoolean()) {
			// Test simple remove by index
			try {
				modify.remove(op.index);
				Assert.assertNull(op.getMessage());
				Assert.assertFalse(element.getElementId().isPresent());
				Assert.assertEquals(preModSize - 1, modify.size());
				Assert.assertEquals(preSize - 1, theCollection.size());
			} catch (UnsupportedOperationException e) {
				Assert.assertNotNull(op.getMessage());
				Assert.assertTrue(op.isError());
				Assert.assertTrue(element.getElementId().isPresent());
				Assert.assertEquals(preModSize, modify.size());
				Assert.assertEquals(preSize, theCollection.size());
			}
		} else {
			// Test remove by element
			try {
				element.remove();
				Assert.assertNull(op.getMessage());
				Assert.assertEquals(preModSize - 1, modify.size());
				Assert.assertEquals(preSize - 1, theCollection.size());
			} catch (UnsupportedOperationException e) {
				Assert.assertNotNull(op.getMessage());
				Assert.assertTrue(op.isError());
				Assert.assertTrue(element.getElementId().isPresent());
				Assert.assertEquals(preModSize, modify.size());
				Assert.assertEquals(preSize, theCollection.size());
			}
		}
	}

	private void removeFromCollection(T value, CollectionOp<T> op, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		CollectionElement<T> element = modify.getElement(value, true);
		if (op == null) {
			Assert.assertNotNull(modify.canRemove(value));
			if (element != null) {
				MutableCollectionElement<T> mutableElement = modify.mutableElement(element.getElementId());
				Assert.assertNotNull(mutableElement.canRemove());
				if (helper.getBoolean()) {
					// Remove by element
					try {
						mutableElement.remove();
						Assert.assertTrue("Should have thrown exception", false);
					} catch (UnsupportedOperationException e) {}
					if (element != null)
						Assert.assertTrue(element.getElementId().isPresent());
					Assert.assertEquals(preModSize, modify.size());
					Assert.assertEquals(preSize, theCollection.size());
				}
			}
			Assert.assertFalse(modify.remove(value));
		} else {
			Assert.assertNotNull(element);
			Assert.assertEquals(op.index, modify.getElementsBefore(element.getElementId()));
			MutableCollectionElement<T> mutableElement = modify.mutableElement(element.getElementId());
			if (op.getMessage() == null)
				Assert.assertNull(mutableElement.canRemove());
			else
				Assert.assertNotNull(mutableElement.canRemove());
			if (helper.getBoolean()) {
				// Simple remove
				boolean removed;
				try {
					removed = modify.remove(value);
					Assert.assertFalse(op.isError());
				} catch (UnsupportedOperationException e) {
					Assert.assertTrue(op.isError());
					removed = false;
				}
				if (removed) {
					Assert.assertNull(op.getMessage());
					if (element != null)
						Assert.assertFalse(element.getElementId().isPresent());
					Assert.assertEquals(preModSize - 1, modify.size());
					Assert.assertEquals(preSize - 1, theCollection.size());
				} else {
					Assert.assertNotNull(op.getMessage());
					if (element != null)
						Assert.assertTrue(element.getElementId().isPresent());
					Assert.assertEquals(preModSize, modify.size());
					Assert.assertEquals(preSize, theCollection.size());
				}
			} else {
				// Test remove by element
				try {
					mutableElement.remove();
					Assert.assertNull(op.getMessage());
					Assert.assertFalse(element.getElementId().isPresent());
					Assert.assertFalse(mutableElement.getElementId().isPresent());
					Assert.assertEquals(preModSize - 1, modify.size());
					Assert.assertEquals(preSize - 1, theCollection.size());
				} catch (UnsupportedOperationException e) {
					Assert.assertNotNull(op.getMessage());
					Assert.assertTrue(op.isError());
					Assert.assertTrue(element.getElementId().isPresent());
					Assert.assertTrue(mutableElement.getElementId().isPresent());
					Assert.assertEquals(preModSize, modify.size());
					Assert.assertEquals(preSize, theCollection.size());
				}
			}
		}
	}

	private void setInCollection(CollectionOp<T> op, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		MutableCollectionElement<T> element = modify.mutableElement(modify.getElement(op.index).getElementId());
		if (element.isEnabled() != null)
			Assert.assertNotNull(op.getMessage());
		else if (element.isAcceptable(op.value) != null)
			Assert.assertNotNull(op.getMessage());
		else
			Assert.assertNull(op.getMessage());
		if (helper.getBoolean()) {
			// Test simple set by index
			try {
				modify.set(op.index, op.value);
				Assert.assertNull(op.getMessage());
			} catch (UnsupportedOperationException | IllegalArgumentException e) {
				Assert.assertNotNull(op.getMessage());
			}
		} else {
			// Test set by element
			try {
				element.set(op.value);
				Assert.assertNull(op.getMessage());
			} catch (UnsupportedOperationException | IllegalArgumentException e) {
				Assert.assertNotNull(op.getMessage());
			}
		}
		Assert.assertTrue(element.getElementId().isPresent());
		Assert.assertEquals(preModSize, modify.size());
		Assert.assertEquals(preSize, theCollection.size());
	}

	private void addAllToCollection(int index, List<CollectionOp<T>> ops, BetterList<T> modify, int subListStart, int subListEnd,
		TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		List<T> values = ops.stream().map(op -> op.value).collect(Collectors.toList());
		boolean modified;
		try {
			if (index >= 0)
				modified = modify.addAll(index, values);
			else
				modified = modify.addAll(values);
		} catch (UnsupportedOperationException | IllegalArgumentException e) {
			e.printStackTrace();
			Assert.assertFalse("Should not throw exceptions", true);
			return;
		}
		int added = 0;
		for (int i = 0; i < ops.size(); i++) {
			CollectionOp<T> op = ops.get(i);
			if (op.getMessage() != null) {
				ops.remove(i);
				i--;
				continue;
			}
			if (modify.getElement(op.value, true) == null)
				Assert.assertTrue(i + ": " + op, false);
			if (theCollection.getElement(op.value, true) == null)
				Assert.assertTrue(i + ": " + op, false);
			added++;
		}
		Assert.assertEquals(modified, added > 0);
		Assert.assertEquals(preModSize + added, modify.size());
		Assert.assertEquals(preSize + added, theCollection.size());
		if (!ops.isEmpty()) {
			// Need to replace the indexes in the operations with the index at which the values were added in the collection (or sub-list)
			for (int i = 0; i < ops.size(); i++) {
				BiTuple<Integer, T> addition = getNextAddition();
				boolean found = false;
				for (int j = i; !found && j < ops.size(); j++) {
					if (theCollection.equivalence().elementEquals(addition.getValue2(), ops.get(j).value)) {
						CollectionOp<T> op_i = ops.get(i);
						ops.set(i, new CollectionOp<>(CollectionChangeType.add, ops.get(j).value, addition.getValue1()));
						if (j != i)
							ops.set(j, op_i);
						found = true;
					}
				}
				Assert.assertTrue(found);
			}
		}
	}

	private void removeAllFromCollection(List<T> values, List<CollectionOp<T>> ops, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		boolean modified = modify.removeAll(values);
		int removed = 0;
		for (CollectionOp<T> op : ops) {
			if (op.getMessage() != null)
				continue;
			removed++;
			if (theCollection instanceof Set)
				Assert.assertNull(modify.getElement(op.value, true));
		}
		Assert.assertEquals(removed > 0, modified);
		Assert.assertEquals(preModSize - removed, modify.size());
		Assert.assertEquals(preSize - removed, theCollection.size());
	}

	private void retainAllInCollection(Collection<T> values, List<CollectionOp<T>> ops, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		boolean modified = modify.retainAll(values);
		int removed = 0;
		for (CollectionOp<T> op : ops) {
			if (op.getMessage() != null)
				continue;
			removed++;
			if (theCollection instanceof Set)
				Assert.assertNull(modify.getElement(op.value, true));
		}
		Assert.assertEquals(removed > 0, modified);
		Assert.assertEquals(preModSize - removed, modify.size());
		Assert.assertEquals(preSize - removed, theCollection.size());
	}

	private void postModify(List<CollectionOp<T>> ops, int subListStart, TestHelper helper) {
		if (subListStart > 0 || ops.stream().anyMatch(op -> op.getMessage() != null))
			ops = ops.stream().filter(op -> op.getMessage() == null)//
			.map(op -> subListStart > 0 ? new CollectionOp<>(null, op.type, op.value, subListStart + op.index) : op)
			.collect(Collectors.toList());
		fromAbove(ops, helper, false);
	}

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
		} catch (IndexOutOfBoundsException | IllegalArgumentException e) { // We'll allow either exception
		}
		try {
			theCollection.add(theCollection.size() + 1, theSupplier.apply(helper));
			Assert.assertFalse("Should have errored", true);
		} catch (IndexOutOfBoundsException | IllegalArgumentException e) { // We'll allow either exception
		}
		try {
			theCollection.set(-1, theSupplier.apply(helper));
			Assert.assertFalse("Should have errored", true);
		} catch (IndexOutOfBoundsException | IllegalArgumentException e) { // We'll allow either exception
		}
		try {
			theCollection.set(theCollection.size() + 1, theSupplier.apply(helper));
			Assert.assertFalse("Should have errored", true);
		} catch (IndexOutOfBoundsException | IllegalArgumentException e) { // We'll allow either exception
		}
	}

	protected void modified(List<CollectionOp<T>> ops, TestHelper helper, boolean propagateUp) {
		for (CollectionOp<T> op : ops) {
			switch (op.type) {
			case add:
				theTester.add(op.index, op.value);
				break;
			case remove:
				theTester.getExpected().remove(op.index);
				break;
			case set:
				theTester.getExpected().set(op.index, op.value);
				break;
			}
		}
		if (propagateUp && theChild != null)
			theChild.fromBelow(ops, helper);
	}

	@Override
	public void check(boolean transComplete) {
		if (transComplete)
			theTester.check();
		else
			theTester.checkNonBatchSynced();

		if (transComplete && theMonitoredElement != null) {
			CollectionElement<T> correct = theCorrectMonitoredElement.get();
			theMonitoredElementTester.check(correct == null ? null : correct.getElementId(), correct == null ? null : correct.get());
		}

		theNewValues.clear();
	}

	@Override
	public <X> ObservableChainLink<X> derive(TestHelper helper) {
		ValueHolder<ObservableChainLink<X>> derived = new ValueHolder<>();
		ValueHolder<CollectionDataFlow<?, ?, X>> derivedFlow = new ValueHolder<>();
		TestHelper.RandomAction action=helper//
			.doAction(1, () -> { // map
				ObservableChainTester.TestValueType nextType = ObservableChainTester.nextType(helper);
				SimpleSettableValue<TypeTransformation<T, X>> txValue = new SimpleSettableValue<>(
					(TypeToken<TypeTransformation<T, X>>) (TypeToken<?>) new TypeToken<Object>() {}, false);
				txValue.set(MappedCollectionLink.transform(theType, nextType, helper), null);
				boolean variableMap = helper.getBoolean();
				CollectionDataFlow<?, ?, T> flow = theFlow;
				if (variableMap)
					flow = flow.refresh(txValue.changes().noInit()); // The refresh has to be UNDER the map
				boolean needsUpdateReeval = !theTester.isCheckingRemovedValues() || variableMap;
				ValueHolder<FlowOptions.MapOptions<T, X>> options = new ValueHolder<>();
				derivedFlow.accept(flow.map((TypeToken<X>) nextType.getType(), src -> txValue.get().map(src), o -> {
					o.manyToOne(txValue.get().isManyToOne());
					if (helper.getBoolean(.95))
						o.withReverse(x -> txValue.get().reverse(x));
					options.accept(o.cache(helper.getBoolean()).fireIfUnchanged(needsUpdateReeval || helper.getBoolean())
						.reEvalOnUpdate(needsUpdateReeval || helper.getBoolean()));
				}));
				theChild = new MappedCollectionLink<>(this, nextType, derivedFlow.get(), helper, !needsUpdateReeval, txValue,
					variableMap, new FlowOptions.MapDef<>(options.get()));
				derived.accept((ObservableChainLink<X>) theChild);
				// TODO mapEquivalent
			})//
			// TODO reverse
			// TODO size
			// TODO find
			// TODO contains
			// TODO containsAny
			// TODO containsAll
			// TODO only
			// TODO reduce
			// TODO flow reverse
			.or(1, () -> { // filter/refresh
				// Getting a java.lang.InternalError: Enclosing method not found when I try to do the TypeToken right.
				// It doesn't matter here anyway
				SimpleSettableValue<Function<T, String>> filterValue = new SimpleSettableValue<>(
					(TypeToken<Function<T, String>>) (TypeToken<?>) new TypeToken<Object>() {}, false);
				filterValue.set(FilteredCollectionLink.filterFor(theType, helper), null);
				boolean variableFilter = helper.getBoolean();
				CollectionDataFlow<?, ?, T> flow = theFlow;
				if (variableFilter)
					flow = flow.refresh(filterValue.changes().noInit()); // The refresh has to be UNDER the filter
				derivedFlow.accept((CollectionDataFlow<?, ?, X>) flow.filter(v -> filterValue.get().apply(v)));
				theChild = new FilteredCollectionLink<>(this, theType, (CollectionDataFlow<?, ?, T>) derivedFlow.get(), helper,
					theTester.isCheckingRemovedValues(), filterValue, variableFilter);
				derived.accept((ObservableChainLink<X>) theChild);
			})//
			// TODO whereContained
			// TODO refreshEach
			// TODO combine
			// TODO flattenValues
			// TODO flatMap
			.or(1, () -> { // sorted
				Comparator<T> compare = SortedCollectionLink.compare(theType, helper);
				derivedFlow.accept((CollectionDataFlow<?, ?, X>) theFlow.sorted(compare));
				theChild = new SortedCollectionLink<>(this, theType, (CollectionDataFlow<?, ?, T>) derivedFlow.get(), helper,
					theTester.isCheckingRemovedValues(), compare);
				derived.accept((ObservableChainLink<X>) theChild);
			})//
			.or(1, () -> { // distinct
				ValueHolder<FlowOptions.UniqueOptions> options = new ValueHolder<>();
				CollectionDataFlow<?, ?, T> flow = theFlow;
				if (helper.getBoolean()) {
					Comparator<T> compare = SortedCollectionLink.compare(theType, helper);
					flow = flow.withEquivalence(Equivalence.of((Class<T>) getType().getRawType(), compare, false));
				}
				derivedFlow.accept((CollectionDataFlow<?, ?, X>) flow.distinct(opts -> {
					// opts.useFirst(helper.getBoolean()).preserveSourceOrder(opts.canPreserveSourceOrder() && helper.getBoolean()); TODO
					options.accept(opts);
				}));
				theChild = new DistinctCollectionLink<>(this, theType, (CollectionDataFlow<?, ?, T>) derivedFlow.get(), theFlow, helper,
					theTester.isCheckingRemovedValues(), options.get(), false);
				derived.accept((ObservableChainLink<X>) theChild);
			})//
			.or(1, () -> { // distinct sorted
				FlowOptions.UniqueOptions options = new FlowOptions.SimpleUniqueOptions(true);
				CollectionDataFlow<?, ?, T> flow = theFlow;
				Comparator<T> compare = SortedCollectionLink.compare(theType, helper);
				options.useFirst(/*TODO helper.getBoolean()*/ false);
				derivedFlow.accept((CollectionDataFlow<?, ?, X>) flow.distinctSorted(compare, options.isUseFirst()));
				theChild = new DistinctCollectionLink<>(this, theType, (CollectionDataFlow<?, ?, T>) derivedFlow.get(), theFlow, helper,
					theTester.isCheckingRemovedValues(), options, false);
				derived.accept((ObservableChainLink<X>) theChild);
			});//
		if(theCollection instanceof ObservableSortedSet){
			ObservableSortedSet<T> sortedSet = (ObservableSortedSet<T>) theCollection;
			action.or(1, () -> { // subSet
				T min, max;
				boolean includeMin, includeMax;
				ObservableSortedSet<T> subSet;
				if (helper.getBoolean(.33)) {
					min = theSupplier.apply(helper);
					includeMin = helper.getBoolean();
					max = null;
					includeMax = true;
					subSet = sortedSet.tailSet(min, includeMin);
				} else if (helper.getBoolean()) {
					max = theSupplier.apply(helper);
					includeMax = helper.getBoolean();
					min = null;
					includeMin = true;
					subSet = sortedSet.headSet(max, includeMax);
				} else {
					T v1 = theSupplier.apply(helper);
					T v2 = theSupplier.apply(helper);
					if (sortedSet.comparator().compare(v1, v2) <= 0) {
						min = v1;
						max = v2;
					} else {
						min = v2;
						max = v1;
					}
					includeMin = helper.getBoolean();
					includeMax = helper.getBoolean();
					subSet = sortedSet.subSet(min, includeMin, max, includeMax);
				}
				derivedFlow.accept((CollectionDataFlow<?, ?, X>) subSet.flow());
				theChild = new SubSetLink<>(this, theType, (ObservableCollection.UniqueSortedDataFlow<?, ?, T>) derivedFlow.get(), helper,
					false, true, min, includeMin, max, includeMax);
				derived.accept((ObservableChainLink<X>) theChild);
			});
			// TODO observeRelative
		}
		action.or(1, () -> {// filterMod
			ValueHolder<ObservableCollection.ModFilterBuilder<T>> filter = new ValueHolder<>();
			derivedFlow.accept((CollectionDataFlow<?, ?, X>) theFlow.filterMod(f -> {
				if (helper.getBoolean(.1))
					f.unmodifiable("Unmodifiable", helper.getBoolean(.75));
				else {
					if (helper.getBoolean(.25))
						f.noAdd("No adds");
					else if (helper.getBoolean(.15))
						f.filterAdd(FilteredCollectionLink.filterFor(theType, helper));
					if (helper.getBoolean(.25))
						f.noRemove("No removes");
					else if (helper.getBoolean(.15))
						f.filterRemove(FilteredCollectionLink.filterFor(theType, helper));
				}
				filter.accept(f);
			}));
			theChild = new ModFilteredCollectionLink<>(this, theType, (CollectionDataFlow<?, ?, T>) derivedFlow.get(), helper,
				new ObservableCollectionDataFlowImpl.ModFilterer<>(filter.get()), theTester.isCheckingRemovedValues());
			derived.accept((ObservableChainLink<X>) theChild);
		})//
		// TODO groupBy
		// TODO groupBy(Sorted)
		;
		action.execute(null);
		return derived.get();
	}
}