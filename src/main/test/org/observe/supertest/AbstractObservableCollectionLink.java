package org.observe.supertest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.observe.Observable;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionTester;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
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
		if (helper.getBoolean())
			theFlow = flow;
		else
			theFlow = theCollection.flow();
		theTester = new ObservableCollectionTester<>(theCollection);
		theSupplier = (Function<TestHelper, T>) ObservableChainTester.SUPPLIERS.get(type);
	}

	protected ObservableCollectionChainLink<?, E> getParent() {
		return theParent;
	}

	protected ObservableCollection<T> getCollection() {
		return theCollection;
	}

	@Override
	public TypeToken<T> getType() {
		return (TypeToken<T>) theType.getType();
	}

	@Override
	public void tryModify(TestHelper helper) {
		CollectionOp<T> op;
		List<CollectionOp<T>> ops;
		int subListStart, subListEnd;
		BetterList<T> modify;
		if (helper.getBoolean(.05)) {
			subListStart = helper.getInt(0, theCollection.size());
			subListEnd = subListStart + helper.getInt(0, theCollection.size() - subListStart);
			modify = theCollection.subList(subListStart, subListEnd);
		} else {
			subListStart = 0;
			subListEnd = theCollection.size();
			modify = theCollection;
		}
		switch (helper.getInt(0, 14)) {
		case 0:
		case 1:
		case 2:
		case 3:
		case 4: // More position-less adds than other ops
			op = new CollectionOp<>(theCollection.equivalence(), theSupplier.apply(helper), -1);
			checkAddable(op, subListStart, subListEnd, helper);
			addToCollection(op, modify, helper);
			if (op.message == null)
				updateForAdd(op, subListStart, helper);
			break;
		case 5: // Add by index
			op = new CollectionOp<>(theCollection.equivalence(), theSupplier.apply(helper),
				subListStart + (modify.isEmpty() ? -1 : helper.getInt(0, modify.size() + 1)));
			checkAddable(op, subListStart, subListEnd, helper);
			addToCollection(op, modify, helper);
			if (op.message == null)
				updateForAdd(op, subListStart, helper);
			break;
		case 6: // addAll
			int length = helper.getInt(0, helper.getInt(0, helper.getInt(0, 1000))); // Aggressively tend smaller
			ops = new ArrayList<>(length);
			for (int i = 0; i < length; i++)
				ops.add(new CollectionOp<>(theCollection.equivalence(), theSupplier.apply(helper), -1));
			int index = subListStart + ((theCollection.isEmpty() || helper.getBoolean()) ? -1 : helper.getInt(0, modify.size() + 1));
			for (int i = 0; i < length; i++)
				checkAddable(ops.get(i), subListStart, subListEnd, helper);
			addAllToCollection(index, ops, modify, helper);
			for (CollectionOp<T> o : ops)
				if (o.message == null)
					updateForAdd(o, subListStart, helper);

			break;
		case 7:
		case 8: // Set
			if (theCollection.isEmpty())
				return;
			op = new CollectionOp<>(theCollection.equivalence(), theSupplier.apply(helper), helper.getInt(0, modify.size()));
			checkSettable(op, subListStart, subListEnd, helper);
			setInCollection(op, modify, helper);
			if (op.message == null)
				updateForSet(op, subListStart, helper);
			break;
		case 9:
			// Remove by value
			op = new CollectionOp<>(theCollection.equivalence(), theSupplier.apply(helper), -1);
			checkRemovable(op, subListStart, subListEnd, helper);
			removeFromCollection(op, modify, helper);
			if (op.message == null)
				updateForRemove(op, subListStart, helper);
			break;
		case 10:
			// Remove by index
			if (theCollection.isEmpty())
				return;
			op = new CollectionOp<>(theCollection.equivalence(), null, helper.getInt(0, modify.size()));
			checkRemovable(op, subListStart, subListEnd, helper);
			removeFromCollection(op, modify, helper);
			if (op.message == null)
				updateForRemove(op, subListStart, helper);
			break;
		case 11: // removeAll
			length = helper.getInt(0, helper.getInt(0, 1000)); // Tend smaller
			ops = new ArrayList<>(length);
			for (int i = 0; i < length; i++)
				ops.add(new CollectionOp<>(theCollection.equivalence(), theSupplier.apply(helper), -1));
			for (int i = 0; i < length; i++)
				checkRemovable(ops.get(i), subListStart, subListEnd, helper);
			removeAllFromCollection(ops, modify, helper);
			for (CollectionOp<T> o : ops)
				if (o.message == null)
					updateForRemove(o, subListStart, helper);
			break;
		case 12: // retainAll
			// Allow for larger, because the smaller the generated collection,
			// the more elements will be removed from the collection
			length = helper.getInt(0, 5000);
			List<T> values = new ArrayList<>(length);
			BetterSet<T> set = theCollection.equivalence().createSet();
			for (int i = 0; i < length; i++) {
				T value = theSupplier.apply(helper);
				values.add(value);
				set.add(value);
			}
			ops = new ArrayList<>();
			for(int i=0;i<theCollection.size();i++){
				T value = theCollection.get(i);
				if(!set.contains(value)){
					op = new CollectionOp<>(theCollection.equivalence(), value, i);
					checkRemovable(op, subListStart, subListEnd, helper);
					ops.add(op);
				}
			}
			retainAllInCollection(values, ops, modify, helper);
			for (CollectionOp<T> o : ops)
				if (o.message == null)
					updateForRemove(o, subListStart, helper);
			break;
		case 13:
			testBounds(helper);
			break;
			// TODO
		}
	}

	private void addToCollection(CollectionOp<T> add, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		if (add.index < 0) {
			if (add.message != null)
				Assert.assertNotNull(modify.canAdd(add.source));
			else
				Assert.assertNull(modify.canAdd(add.source));
			if (helper.getBoolean()) {
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
					Assert.assertEquals(preModSize + 1, modify.size());
					Assert.assertEquals(preSize + 1, theCollection.size());
				} else {
					Assert.assertNotNull(add.message);
					Assert.assertEquals(preModSize, modify.size());
					Assert.assertEquals(preSize, theCollection.size());
				}
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
						return;
					} else {
						Assert.assertNull(add.message);
					}
					Assert.assertEquals(preModSize + 1, modify.size());
					Assert.assertEquals(preSize + 1, theCollection.size());
					int index = modify.getElementsBefore(element.getElementId());
					Assert.assertTrue(index >= 0 && index <= preModSize);
				} catch (UnsupportedOperationException | IllegalArgumentException e) {
					Assert.assertNotNull(add.message);
					add.isError = true;
					return;
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
				} else {
					ElementId newElement = element.add(add.source, addLeft);
					Assert.assertNotNull(newElement);
					Assert.assertEquals(preModSize + 1, modify.size());
					Assert.assertEquals(preSize + 1, theCollection.size());
					add.result = element.get();
					Assert.assertTrue(theCollection.equivalence().elementEquals(modify.getElement(newElement).get(), add.source));
					int index = modify.getElementsBefore(newElement);
					Assert.assertTrue(index >= 0 && index <= preModSize);
				}
			}
		}
		if (add.message == null)
			add.result = add.source; // No mapping if we're the root
	}

	private void removeFromCollection(CollectionOp<T> op, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		if (op.index < 0) {
			if (op.message != null)
				Assert.assertNotNull(modify.canRemove(op.source));
			else
				Assert.assertNull(modify.canRemove(op.source));
			CollectionElement<T> element = modify.getElement(op.source, helper.getBoolean());
			if (element == null)
				Assert.assertNotNull(op.message);
			else {
				MutableCollectionElement<T> mutableElement = modify.mutableElement(element.getElementId());
				if (op.message != null)
					Assert.assertNotNull(mutableElement.canRemove());
				else
					Assert.assertNull(mutableElement.canRemove());
			}
			if (element == null || helper.getBoolean()) {
				// Test simple remove value
				boolean removed;
				try {
					removed = modify.remove(op.source);
				} catch (UnsupportedOperationException | IllegalArgumentException e) {
					removed = false;
				}
				if (removed) {
					Assert.assertNull(op.message);
					Assert.assertFalse(element.getElementId().isPresent());
					Assert.assertEquals(preModSize - 1, modify.size());
					Assert.assertEquals(preSize - 1, theCollection.size());
				} else {
					Assert.assertNotNull(op.message);
					Assert.assertTrue(element.getElementId().isPresent());
					Assert.assertEquals(preModSize, modify.size());
					Assert.assertEquals(preSize, theCollection.size());
				}
			} else {
				// Test remove by element
				MutableCollectionElement<T> mutableElement = modify.mutableElement(element.getElementId());
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
		} else {
			MutableCollectionElement<T> element = modify.mutableElement(modify.getElement(op.index).getElementId());
			if (op.message != null)
				Assert.assertNotNull(element.canRemove());
			else
				Assert.assertNull(element.canRemove());
			if (helper.getBoolean()) {
				// Test simple remove by index
				try {
					op.result = modify.remove(op.index);
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
					op.result = element.get();
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
				op.result = modify.set(op.index, op.source);
				Assert.assertNull(op.message);
			} catch (UnsupportedOperationException | IllegalArgumentException e) {
				Assert.assertNotNull(op.message);
			}
		} else {
			// Test set by element
			try {
				op.result = element.get();
				element.set(op.source);
				Assert.assertNull(op.message);
			} catch (UnsupportedOperationException e) {
				Assert.assertNotNull(op.message);
			}
		}
		Assert.assertTrue(element.getElementId().isPresent());
		Assert.assertEquals(preModSize, modify.size());
		Assert.assertEquals(preSize, theCollection.size());
	}

	private void addAllToCollection(int index, List<CollectionOp<T>> ops, BetterList<T> modify, TestHelper helper) {
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
		for (CollectionOp<T> op : ops) {
			if (op.message != null)
				continue;
			Assert.assertNotNull(modify.getElement(op.source, true));
			Assert.assertNotNull(theCollection.getElement(op.source, true));
			added++;
		}
		Assert.assertEquals(modified, added > 0);
		Assert.assertEquals(modify.size(), preModSize + added);
		Assert.assertEquals(theCollection.size(), preSize + added);
	}

	private void removeAllFromCollection(List<CollectionOp<T>> ops, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		List<T> values = ops.stream().map(op -> op.source).collect(Collectors.toList());
		Boolean modified;
		try {
			modified = modify.removeAll(values);
		} catch (UnsupportedOperationException | IllegalArgumentException e) {
			modified = null;
		}
		int removed = 0;
		for (CollectionOp<T> op : ops) {
			if (op.isError)
				Assert.assertNull(modified);
			else if (op.message != null)
				continue;
			if (theCollection instanceof Set)
				Assert.assertNull(modify.getElement(op.source, true));
		}
		if (modified != null)
			Assert.assertEquals(modified.booleanValue(), removed > 0);
		Assert.assertEquals(modify.size(), preModSize - removed);
		Assert.assertEquals(theCollection.size(), preSize - removed);
	}

	private void retainAllInCollection(Collection<T> values, List<CollectionOp<T>> ops, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		Boolean modified;
		try {
			modified = modify.removeAll(values);
		} catch (UnsupportedOperationException | IllegalArgumentException e) {
			modified = null;
		}
		int removed = 0;
		for (CollectionOp<T> op : ops) {
			if (op.isError)
				Assert.assertNull(modified);
			else if (op.message != null)
				continue;
			if (theCollection instanceof Set)
				Assert.assertNull(modify.getElement(op.source, true));
		}
		if (modified != null)
			Assert.assertEquals(modified.booleanValue(), removed > 0);
		Assert.assertEquals(modify.size(), preModSize - removed);
		Assert.assertEquals(theCollection.size(), preSize - removed);
	}

	private void updateForAdd(CollectionOp<T> add, int subListStart, TestHelper helper) {
		addedFromAbove(add.index <= 0 ? -1 : subListStart + add.index, add.source, helper);
		if (theChild != null)
			theChild.addedFromBelow(add.index, add.source, helper);
	}

	private void updateForRemove(CollectionOp<T> remove, int subListStart, TestHelper helper) {
		int index = removedFromAbove(remove.index <= 0 ? -1 : subListStart + remove.index, remove.source, helper);
		if (theChild != null)
			theChild.removedFromBelow(index, helper);
	}

	private void updateForSet(CollectionOp<T> set, int subListStart, TestHelper helper) {
		setFromAbove(set.index <= 0 ? -1 : subListStart + set.index, set.source, helper);
		if (theChild != null)
			theChild.setFromBelow(set.index, set.source, helper);
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

	protected void added(int index, T value, TestHelper helper) {
		theTester.add(index, value);
		if (theChild != null)
			theChild.addedFromBelow(index, value, helper);
	}

	protected void removed(int index, TestHelper helper) {
		theTester.getExpected().remove(index);
		if (theChild != null)
			theChild.removedFromBelow(index, helper);
	}

	protected void set(int index, T value, TestHelper helper) {
		theTester.getExpected().set(index, value);
		if (theChild != null)
			theChild.setFromBelow(index, value, helper);
	}

	@Override
	public void check() {
		theTester.check();
	}

	@Override
	public ObservableChainLink<?> derive(TestHelper helper) {
		ObservableChainLink<?> derived;
		switch (helper.getInt(0, 1)) {
		case 0:
			derived = theChild = new MappedCollectionLink<>(this, theType, theFlow, helper);
			// TODO mapEquivalent
			break;
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
			// TODO filterStatic
			// TODO whereContained
			// TODO withEquivalence
			// TODO refresh
			// TODO refreshEach
			// TODO combine
			// TODO flattenValues
			// TODO flatMap
			// TODO sorted
			// TODO distinct
			// TODO distinctSorted
			// TODO filterMod
			// TODO groupBy
			// TODO groupBy(Sorted)
		}
		return derived;
	}
}