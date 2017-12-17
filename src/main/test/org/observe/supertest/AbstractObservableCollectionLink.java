package org.observe.supertest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.observe.Observable;
import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionTester;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;

import com.google.common.reflect.TypeToken;

abstract class AbstractObservableCollectionLink<E, T> implements ObservableCollectionChainLink<E, T> {
	private final ObservableCollectionChainLink<?, E> theParent;
	private final TestValueType theType;
	private final CollectionDataFlow<?, ?, T> theFlow;
	private final ObservableCollection<T> theCollection;
	private final ObservableCollectionTester<T> theTester;
	private ObservableCollectionChainLink<T, ?> theChild;
	private final Function<TestHelper, T> theSupplier;

	AbstractObservableCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type,
		CollectionDataFlow<?, ?, T> flow, TestHelper helper) {
		theParent = parent;
		theType = type;
		if (flow.supportsPassive() && helper.getBoolean())
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
		if (helper.getBoolean())
			theFlow = flow;
		else
			theFlow = theCollection.flow();
		theTester = new ObservableCollectionTester<>(theCollection);
	}

	@Override
	public ObservableCollectionChainLink<?, E> getParent() {
		return theParent;
	}

	@Override
	public ObservableCollection<T> getCollection() {
		return theCollection;
	}

	protected List<T> getExpected() {
		return theTester.getExpected();
	}

	@Override
	public String printValue() {
		return theCollection.size() + theCollection.toString();
	}

	@Override
	public TypeToken<T> getType() {
		return (TypeToken<T>) theType.getType();
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
			if(ObservableChainTester.DEBUG_PRINT)
				System.out.println("subList(" + subListStart + ", " + subListEnd + ")");
		} else {
			subListStart = 0;
			subListEnd = theCollection.size();
			modify = theCollection;
		}
		helper.doAction(5, () -> { // More position-less adds than other ops
			CollectionOp<T> op = new CollectionOp<>(theSupplier.apply(helper), -1);
			if (ObservableChainTester.DEBUG_PRINT)
				System.out.println("Add " + op);
			checkAddable(op, subListStart, subListEnd, helper);
			int index = addToCollection(op, modify, helper);
			if (op.message == null) {
				op = new CollectionOp<>(op.source, index);
				updateForAdd(op, subListStart, helper);
			}
		}).or(1, () -> { // Add by index
			CollectionOp<T> op = new CollectionOp<>(theSupplier.apply(helper), helper.getInt(0, modify.size() + 1));
			if (ObservableChainTester.DEBUG_PRINT)
				System.out.println("Add " + op);
			checkAddable(op, subListStart, subListEnd, helper);
			addToCollection(op, modify, helper);
			if (op.message == null)
				updateForAdd(op, subListStart, helper);
		}).or(1, () -> { // addAll
			int length = (int) helper.getDouble(0, 100, 1000); // Aggressively tend smaller
			int index = helper.getBoolean() ? -1 : helper.getInt(0, modify.size() + 1);
			List<CollectionOp<T>> ops = new ArrayList<>(length);
			for (int i = 0; i < length; i++)
				ops.add(new CollectionOp<>(theSupplier.apply(helper), index));
			checkAddable(ops, subListStart, subListEnd, helper);
			if (ObservableChainTester.DEBUG_PRINT) {
				String msg = "Add all ";
				if (index >= 0) {
					msg += "@" + index;
					if (index > 0)
						msg += ", after " + modify.get(index - 1);
					msg += " ";
				}
				System.out.println(msg + ops.size() + ops);
			}
			addAllToCollection(index, ops, modify, subListStart, subListEnd, helper);
			for (CollectionOp<T> o : ops){
				if (o.message == null)
					updateForAdd(o, subListStart, helper);
			}
		}).or(2, () -> { // Set
			if (modify.isEmpty()) {
				if (ObservableChainTester.DEBUG_PRINT)
					System.out.println("Set, but empty");
				return;
			}
			CollectionOp<T> op = new CollectionOp<>(theSupplier.apply(helper), helper.getInt(0, modify.size()));
			if (ObservableChainTester.DEBUG_PRINT)
				System.out.println("Set " + op);
			checkSettable(op, subListStart, subListEnd, helper);
			setInCollection(op, modify, helper);
			if (op.message == null)
				updateForSet(op, subListStart, helper);
		}).or(1, () -> {// Remove by value
			T value = theSupplier.apply(helper);
			if (ObservableChainTester.DEBUG_PRINT)
				System.out.println("Remove " + value);
			CollectionOp<T> op = null;
			for (int i = 0; i < modify.size(); i++) {
				if (theCollection.equivalence().elementEquals(modify.get(i), value)) {
					if (ObservableChainTester.DEBUG_PRINT)
						System.out.println("\t\tIndex " + i);
					op = new CollectionOp<>(value, i);
					checkRemovable(op, subListStart, subListEnd, helper);
					break;
				}
			}
			removeFromCollection(value, op, modify, helper);
			if (op != null && op.message == null)
				updateForRemove(op, subListStart, helper);
		}).or(1, () -> {// Remove by index
			if (modify.isEmpty()) {
				if (ObservableChainTester.DEBUG_PRINT)
					System.out.println("Remove, but empty");
				return;
			}
			int index = helper.getInt(0, modify.size());
			CollectionOp<T> op = new CollectionOp<>(modify.get(index), index);
			if (ObservableChainTester.DEBUG_PRINT)
				System.out.println("Remove " + op);
			checkRemovable(op, subListStart, subListEnd, helper);
			removeFromCollection(op, modify, helper);
			if (op.message == null)
				updateForRemove(op, subListStart, helper);
		}).or(1, () -> { // removeAll
			int length = (int) helper.getDouble(0, 250, 1000); // Tend smaller
			List<T> values = new ArrayList<>(length);
			BetterSet<T> set = theCollection.equivalence().createSet();
			for (int i = 0; i < length; i++) {
				T value = theSupplier.apply(helper);
				values.add(value);
				set.add(value);
			}
			if (ObservableChainTester.DEBUG_PRINT)
				System.out.println("Remove all " + values.size() + values);
			List<CollectionOp<T>> ops = new ArrayList<>(length);
			for (int i = 0; i < modify.size(); i++) {
				T value = modify.get(i);
				if (set.contains(value)) {
					CollectionOp<T> op = new CollectionOp<>(value, i);
					checkRemovable(op, subListStart, subListEnd, helper);
					ops.add(op);
				}
			}
			if (ObservableChainTester.DEBUG_PRINT)
				System.out.println("\tShould remove " + ops.size() + ops);
			removeAllFromCollection(values, ops, modify, helper);
			// Do this in reverse, so the indexes are right
			for (int i = ops.size() - 1; i >= 0; i--) {
				CollectionOp<T> o = ops.get(i);
				if (o.message == null)
					updateForRemove(o, subListStart, helper);
			}
		}).or(1, () -> { // retainAll
			// Allow for larger, because the smaller the generated collection,
			// the more elements will be removed from the collection
			int length = helper.getInt(0, 5000);
			List<T> values = new ArrayList<>(length);
			Set<T> set = theCollection.equivalence().createSet();
			for (int i = 0; i < length; i++) {
				T value = theSupplier.apply(helper);
				values.add(value);
				set.add(value);
			}
			if (ObservableChainTester.DEBUG_PRINT)
				System.out.println("Retain all " + values.size() + values);
			List<CollectionOp<T>> ops = new ArrayList<>();
			for (int i = 0; i < modify.size(); i++) {
				T value = modify.get(i);
				if(!set.contains(value)){
					CollectionOp<T> op = new CollectionOp<>(value, i);
					checkRemovable(op, subListStart, subListEnd, helper);
					ops.add(op);
				}
			}
			if (ObservableChainTester.DEBUG_PRINT)
				System.out.println("\tShould remove " + ops.size() + ops);
			retainAllInCollection(values, ops, modify, helper);
			// Do this in reverse, so the indexes are right
			for (int i = ops.size() - 1; i >= 0; i--) {
				CollectionOp<T> o = ops.get(i);
				if (o.message == null)
					updateForRemove(o, subListStart, helper);
			}
		}).or(1, () -> {
			if (ObservableChainTester.DEBUG_PRINT)
				System.out.println("[" + getLinkIndex() + "]: Check bounds");
			testBounds(helper);
		})//
		.execute();
	}

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
			if (add.message != null)
				Assert.assertNotNull(modify.canAdd(add.source));
			else
				Assert.assertNull(modify.canAdd(add.source));
			// Test simple add value
			CollectionElement<T> element;
			try {
				element = modify.addElement(add.source, helper.getBoolean());
			} catch (UnsupportedOperationException | IllegalArgumentException e) {
				element = null;
			}
			if (element != null) {
				Assert.assertTrue(element.getElementId().isPresent());
				Assert.assertNull(add.message);
				Assert.assertTrue(theCollection.equivalence().elementEquals(add.source, element.get()));
				Assert.assertEquals(preModSize + 1, modify.size());
				Assert.assertEquals(preSize + 1, theCollection.size());
				return modify.getElementsBefore(element.getElementId());
			} else {
				Assert.assertNotNull(add.message);
				Assert.assertEquals(preModSize, modify.size());
				Assert.assertEquals(preSize, theCollection.size());
				return -1;
			}
		} else {
			if (modify.isEmpty() || helper.getBoolean()) {
				// Test simple add by index
				try {
					CollectionElement<T> element = modify.addElement(add.index, add.source);
					if (element == null) {
						Assert.assertNotNull(add.message);
						Assert.assertEquals(preModSize, modify.size());
						add.message = "";
						return -1;
					} else {
						Assert.assertNull(add.message);
						Assert.assertTrue(theCollection.equivalence().elementEquals(add.source, element.get()));
					}
					Assert.assertEquals(preModSize + 1, modify.size());
					Assert.assertEquals(preSize + 1, theCollection.size());
					int index = modify.getElementsBefore(element.getElementId());
					Assert.assertTrue(index >= 0 && index <= preModSize);
					return index;
				} catch (UnsupportedOperationException | IllegalArgumentException e) {
					Assert.assertNotNull(add.message);
					add.isError = true;
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
				if (add.message != null)
					Assert.assertNotNull(element.canAdd(add.source, addLeft));
				else
					Assert.assertNull(element.canAdd(add.source, addLeft));
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
					Assert.assertEquals(preModSize, modify.size());
					Assert.assertEquals(preSize, theCollection.size());
					return -1;
				} else {
					ElementId newElement = element.add(add.source, addLeft);
					Assert.assertNotNull(newElement);
					Assert.assertEquals(preModSize + 1, modify.size());
					Assert.assertEquals(preSize + 1, theCollection.size());
					Assert.assertTrue(theCollection.equivalence().elementEquals(modify.getElement(newElement).get(), add.source));
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
		if (op.message != null)
			Assert.assertNotNull(element.canRemove());
		else
			Assert.assertNull(element.canRemove());
		if (helper.getBoolean()) {
			// Test simple remove by index
			try {
				modify.remove(op.index);
				Assert.assertNull(op.message);
				Assert.assertFalse(element.getElementId().isPresent());
				Assert.assertEquals(preModSize - 1, modify.size());
				Assert.assertEquals(preSize - 1, theCollection.size());
			} catch (UnsupportedOperationException e) {
				Assert.assertNotNull(op.message);
				Assert.assertTrue(element.getElementId().isPresent());
				Assert.assertEquals(preModSize, modify.size());
				Assert.assertEquals(preSize, theCollection.size());
			}
		} else {
			// Test remove by element
			try {
				element.remove();
				Assert.assertNull(op.message);
				Assert.assertEquals(preModSize - 1, modify.size());
				Assert.assertEquals(preSize - 1, theCollection.size());
			} catch (UnsupportedOperationException e) {
				Assert.assertNotNull(op.message);
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
			if (op.message == null)
				Assert.assertNull(mutableElement.canRemove());
			else
				Assert.assertNotNull(mutableElement.canRemove());
			if (helper.getBoolean()) {
				// Simple remove
				boolean removed;
				try {
					removed = modify.remove(value);
				} catch (UnsupportedOperationException e) {
					removed = false;
				}
				if (removed) {
					Assert.assertNull(op.message);
					if (element != null)
						Assert.assertFalse(element.getElementId().isPresent());
					Assert.assertEquals(preModSize - 1, modify.size());
					Assert.assertEquals(preSize - 1, theCollection.size());
				} else {
					Assert.assertNotNull(op.message);
					if (element != null)
						Assert.assertTrue(element.getElementId().isPresent());
					Assert.assertEquals(preModSize, modify.size());
					Assert.assertEquals(preSize, theCollection.size());
				}
			} else {
				// Test remove by element
				try {
					mutableElement.remove();
					Assert.assertNull(op.message);
					Assert.assertFalse(element.getElementId().isPresent());
					Assert.assertFalse(mutableElement.getElementId().isPresent());
					Assert.assertEquals(preModSize - 1, modify.size());
					Assert.assertEquals(preSize - 1, theCollection.size());
				} catch (UnsupportedOperationException e) {
					Assert.assertNotNull(op.message);
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
			Assert.assertNotNull(op.message);
		else if (element.isAcceptable(op.source) != null)
			Assert.assertNotNull(op.message);
		else
			Assert.assertNull(op.message);
		if (helper.getBoolean()) {
			// Test simple set by index
			try {
				modify.set(op.index, op.source);
				Assert.assertNull(op.message);
			} catch (UnsupportedOperationException | IllegalArgumentException e) {
				Assert.assertNotNull(op.message);
			}
		} else {
			// Test set by element
			try {
				element.set(op.source);
				Assert.assertNull(op.message);
			} catch (UnsupportedOperationException | IllegalArgumentException e) {
				Assert.assertNotNull(op.message);
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
		List<T> values = ops.stream().map(op -> op.source).collect(Collectors.toList());
		boolean modified;
		try {
			if (index >= 0)
				modified = modify.addAll(index, values);
			else
				modified = modify.addAll(values);
		} catch (UnsupportedOperationException | IllegalArgumentException e) {
			Assert.assertFalse("Should not throw exceptions", true);
			return;
		}
		int added = 0;
		for (int i = 0; i < ops.size(); i++) {
			CollectionOp<T> op = ops.get(i);
			if (op.message != null) {
				ops.remove(i);
				i--;
				continue;
			}
			if (modify.getElement(op.source, true) == null)
				Assert.assertTrue(i + ": " + op, false);
			if (theCollection.getElement(op.source, true) == null)
				Assert.assertTrue(i + ": " + op, false);
			added++;
		}
		Assert.assertEquals(modified, added > 0);
		Assert.assertEquals(modify.size(), preModSize + added);
		Assert.assertEquals(theCollection.size(), preSize + added);
		List<T> expected = theTester.getExpected();
		if (!ops.isEmpty()) {
			// Need to replace the indexes in the operations with the index at which the values were added in the collection (or sub-list)
			NavigableSet<Integer> indexes = new TreeSet<>();
			BetterList<T> addedList = index >= 0 ? modify.subList(index, index + added) : modify;
			int addedListStart = index >= 0 ? index : 0;
			for (int i = 0; i < ops.size(); i++) {
				CollectionOp<T> op = ops.get(i);
				CollectionElement<T> el = addedList.getElement(op.source, true);
				int elIndex = addedList.getElementsBefore(el.getElementId());
				// If the found element is a duplicate that was either already present or whose index has previously been used, find a new
				// element
				while (el != null) {
					if (indexes.contains(elIndex)) {
						el = addedList.subList(elIndex + 1, addedList.size()).getElement(op.source, true);
						elIndex = addedList.getElementsBefore(el.getElementId());
						continue;
					} else if (index < 0) {
						int expectedIndex = subListStart + addedListStart + elIndex - indexes.headSet(elIndex).size();
						if (expectedIndex >= subListStart && expectedIndex < subListEnd
							&& theCollection.equivalence().elementEquals(expected.get(expectedIndex), op.source)) {
							el = addedList.subList(elIndex + 1, addedList.size()).getElement(op.source, true);
							elIndex = addedList.getElementsBefore(el.getElementId());
							continue;
						}
					}
					indexes.add(elIndex);
					break;
				}
				elIndex += addedListStart;
				Assert.assertNotNull(el);
				if (index >= 0)
					Assert.assertTrue(elIndex + ">=" + index + "+" + added, elIndex < (index < 0 ? preModSize : index) + added);
				ops.set(i, new CollectionOp<>(op.source, elIndex));
			}
			Collections.sort(ops, (op1, op2) -> op1.index - op2.index);
			if (index >= 0) {
				// Ensures that all new elements were added in the right position range
				Assert.assertEquals(ops.get(0).index, index);
				Assert.assertEquals(ops.get(ops.size() - 1).index, index + added - 1);
			}
		}
	}

	private void removeAllFromCollection(List<T> values, List<CollectionOp<T>> ops, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		boolean modified = modify.removeAll(values);
		int removed = 0;
		for (CollectionOp<T> op : ops) {
			if (op.message != null)
				continue;
			removed++;
			if (theCollection instanceof Set)
				Assert.assertNull(modify.getElement(op.source, true));
		}
		Assert.assertEquals(modified, removed > 0);
		Assert.assertEquals(modify.size(), preModSize - removed);
		Assert.assertEquals(theCollection.size(), preSize - removed);
	}

	private void retainAllInCollection(Collection<T> values, List<CollectionOp<T>> ops, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		boolean modified = modify.retainAll(values);
		int removed = 0;
		for (CollectionOp<T> op : ops) {
			if (op.message != null)
				continue;
			removed++;
			if (theCollection instanceof Set)
				Assert.assertNull(modify.getElement(op.source, true));
		}
		Assert.assertEquals(modified, removed > 0);
		Assert.assertEquals(modify.size(), preModSize - removed);
		Assert.assertEquals(theCollection.size(), preSize - removed);
	}

	private void updateForAdd(CollectionOp<T> add, int subListStart, TestHelper helper) {
		if (add.message != null)
			return;
		addedFromAbove(add.index < 0 ? -1 : subListStart + add.index, add.source, helper, false);
	}

	private void updateForRemove(CollectionOp<T> remove, int subListStart, TestHelper helper) {
		if (remove.message != null)
			return;
		removedFromAbove(subListStart + remove.index, remove.source, helper, false);
	}

	private void updateForSet(CollectionOp<T> set, int subListStart, TestHelper helper) {
		if (set.message != null)
			return;
		setFromAbove(subListStart + set.index, set.source, helper, false);
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

	protected void added(int index, T value, TestHelper helper, boolean propagateUp) {
		if (index >= 0)
			theTester.add(index, value);
		else
			theTester.add(value);
		if (propagateUp && theChild != null)
			theChild.addedFromBelow(index, value, helper);
	}

	protected void removed(int index, TestHelper helper, boolean propagateUp) {
		if (index < 0)
			return;
		theTester.getExpected().remove(index);
		if (propagateUp && theChild != null)
			theChild.removedFromBelow(index, helper);
	}

	protected void set(int index, T value, TestHelper helper, boolean propagateUp) {
		theTester.getExpected().set(index, value);
		if (propagateUp && theChild != null)
			theChild.setFromBelow(index, value, helper);
	}

	@Override
	public void check(boolean transComplete) {
		if (transComplete)
			theTester.check();
		else
			theTester.checkNonBatchSynced();
	}

	@Override
	public <X> ObservableChainLink<X> derive(TestHelper helper) {
		ValueHolder<ObservableChainLink<X>> derived = new ValueHolder<>();
		ValueHolder<CollectionDataFlow<?, ?, X>> derivedFlow = new ValueHolder<>();
		helper.doAction(1, () -> { // map
			ObservableChainTester.TestValueType nextType = ObservableChainTester.nextType(helper);
			ObservableChainTester.TypeTransformation<T, X> transform = ObservableChainTester.transform(theType, nextType, helper);
			ValueHolder<FlowOptions.MapOptions<T, X>> options = new ValueHolder<>();
			derivedFlow.accept(theFlow.map((TypeToken<X>) nextType.getType(), transform::map, o -> {
				if (helper.getBoolean(.95))
					o.withReverse(transform::reverse);
				options.accept(o.cache(helper.getBoolean()).fireIfUnchanged(helper.getBoolean()).reEvalOnUpdate(helper.getBoolean()));
			}));
			theChild = new MappedCollectionLink<>(this, nextType, derivedFlow.get(), helper, transform,
				new FlowOptions.MapDef<>(options.get()));
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
		// TODO subset
		// TODO observeRelative
		// TODO flow reverse
		// TODO filter
		// TODO whereContained
		// TODO withEquivalence
		// TODO refresh
		// TODO refreshEach
		// TODO combine
		// TODO flattenValues
		// TODO flatMap
		// TODO sorted
		.or(1, () -> {
			// distinct
			ValueHolder<FlowOptions.UniqueOptions> options = new ValueHolder<>();
			derivedFlow.accept((CollectionDataFlow<?, ?, X>) theFlow.distinct(opts -> {
				// opts.useFirst(helper.getBoolean()).preserveSourceOrder(opts.canPreserveSourceOrder() && helper.getBoolean());
				options.accept(opts);
			}));
			theChild = new DistinctCollectionLink<>(this, theType, (CollectionDataFlow<?, ?, T>) derivedFlow.get(), helper,
				options.get());
			derived.accept((ObservableChainLink<X>) theChild);
		})
		// TODO distinctSorted
		// TODO filterMod
		// TODO groupBy
		// TODO groupBy(Sorted)
		.execute();
		return derived.get();
	}
}