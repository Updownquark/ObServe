package org.observe.supertest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Assert;
import org.observe.Observable;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionTester;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;

abstract class AbstractObservableCollectionLink<E> implements ObservableCollectionChainLink<E, E> {
	private final ObservableCollectionChainLink<?, E> theParent;
	private final CollectionDataFlow<?, ?, E> theFlow;
	private final ObservableCollection<E> theCollection;
	private final ObservableCollectionTester<E> theTester;
	private ObservableCollectionChainLink<E, ?> theChild;
	private final Function<TestHelper, E> theSupplier;

	AbstractObservableCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type,
		CollectionDataFlow<?, ?, E> flow, TestHelper helper) {
		theParent = parent;
		if (flow.supportsPassive() && helper.getBoolean())
			theCollection = flow.collectPassive();
		else
			theCollection = flow.collectActive(Observable.empty);
		if (helper.getBoolean())
			theFlow = flow;
		else
			theFlow = theCollection.flow();
		theTester = new ObservableCollectionTester<>(theCollection);
		theSupplier = (Function<TestHelper, E>) ObservableChainTester.SUPPLIERS.get(type);
	}

	@Override
	public void tryModify(TestHelper helper) {
		CollectionOp<E> op;
		List<CollectionOp<E>> ops;
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
			op = new CollectionOp<>(theSupplier.apply(helper), -1);
			checkAddFromAbove(op);
			addToCollection(op, helper);
			break;
		case 5: // Add by index
			op = new CollectionOp<>(theSupplier.apply(helper),
				subListStart + (modify.isEmpty() ? -1 : helper.getInt(0, modify.size() + 1)));
			checkAddFromAbove(op);
			addToCollection(op, helper);
			break;
		case 6: // addAll
			int length = helper.getInt(0, helper.getInt(0, helper.getInt(0, 1000))); // Aggressively tend smaller
			ops = new ArrayList<>(length);
			for (int i = 0; i < length; i++)
				ops.add(new CollectionOp<>(theSupplier.apply(helper), -1));
			int index = subListStart + ((theCollection.isEmpty() || helper.getBoolean()) ? -1 : helper.getInt(0, modify.size() + 1));
			checkAddAll(index, ops);
			addAllToCollection(index, ops);
			break;
		case 7:
		case 8: // Set
			if (theCollection.isEmpty())
				return;
			op = new CollectionOp<>(theSupplier.apply(helper), helper.getInt(0, modify.size()));
			checkSetFromAbove(op);
			setInCollection(op);
			break;
		case 9:
			op = new CollectionOp<>(theSupplier.apply(helper), -1);
			checkRemoveFromAbove(op);
			removeFromCollection(op);
			break;
		case 10:
			if (theCollection.isEmpty())
				return;
			op = new CollectionOp<>(null, helper.getInt(0, modify.size()));
			checkRemoveFromAbove(op);
			removeFromCollection(op);
			break;
		case 11: // removeAll
			length = helper.getInt(0, helper.getInt(0, helper.getInt(0, 1000))); // Aggressively tend smaller
			ops = new ArrayList<>(length);
			for (int i = 0; i < length; i++)
				ops.add(new CollectionOp<>(theSupplier.apply(helper), -1));
			checkRemoveAll(ops);
			removeAllFromCollection(ops);
			break;
		case 12: // retainAll
			length = helper.getInt(0, 1000);
			ops = new ArrayList<>(length);
			for (int i = 0; i < length; i++)
				ops.add(new CollectionOp<>(theSupplier.apply(helper), -1));
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

	private void addToCollection(CollectionOp<E> add, TestHelper helper) {
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
	public ObservableChainLink<?> derive(TestHelper helper) {
		return ObservableChainTester.deriveFromFlow(this, theFlow, helper);
	}

	@Override
	public void check() {
		theTester.check();
	}
}