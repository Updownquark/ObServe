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
import org.qommons.collect.BetterSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;

abstract class AbstractObservableCollectionLink<E> implements ObservableCollectionChainLink<E, E> {
	private final ObservableCollectionChainLink<?, E> theParent;
	private final TestValueType theType;
	private final CollectionDataFlow<?, ?, E> theFlow;
	private final ObservableCollection<E> theCollection;
	private final ObservableCollectionTester<E> theTester;
	private ObservableCollectionChainLink<E, ?> theChild;
	private final Function<TestHelper, E> theSupplier;

	AbstractObservableCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type,
		CollectionDataFlow<?, ?, E> flow, TestHelper helper) {
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
		theSupplier = (Function<TestHelper, E>) ObservableChainTester.SUPPLIERS.get(type);
	}

	@Override
	public void tryModify(TestHelper helper) {
		CollectionOp<E> op;
		CollectionOp<E> copyOp;
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
			op = new CollectionOp<>(theCollection.equivalence(), theSupplier.apply(helper), -1);
			copyOp = op.clone();
			checkAddFromAbove(op);
			Assert.assertEquals(op, checkAddable(copyOp));
			addToCollection(op, helper);
			break;
		case 5: // Add by index
			op = new CollectionOp<>(theCollection.equivalence(), theSupplier.apply(helper),
				subListStart + (modify.isEmpty() ? -1 : helper.getInt(0, modify.size() + 1)));
			copyOp = op.clone();
			checkAddFromAbove(op);
			Assert.assertEquals(op, checkAddable(copyOp));
			addToCollection(op, helper);
			break;
		case 6: // addAll
			int length = helper.getInt(0, helper.getInt(0, helper.getInt(0, 1000))); // Aggressively tend smaller
			ops = new ArrayList<>(length);
			for (int i = 0; i < length; i++)
				ops.add(new CollectionOp<>(theCollection.equivalence(), theSupplier.apply(helper), -1));
			int index = subListStart + ((theCollection.isEmpty() || helper.getBoolean()) ? -1 : helper.getInt(0, modify.size() + 1));
			for (int i = 0; i < length; i++) {
				copyOp = ops.get(i).clone();
				checkAddFromAbove(ops.get(i));
				Assert.assertEquals(ops.get(i), checkAddable(copyOp));
			}
			addAllToCollection(index, ops);
			break;
		case 7:
		case 8: // Set
			if (theCollection.isEmpty())
				return;
			op = new CollectionOp<>(theCollection.equivalence(), theSupplier.apply(helper), helper.getInt(0, modify.size()));
			copyOp = op.clone();
			checkSetFromAbove(op);
			Assert.assertEquals(op, checkSettable(copyOp));
			setInCollection(op);
			break;
		case 9:
			op = new CollectionOp<>(theCollection.equivalence(), theSupplier.apply(helper), -1);
			copyOp = op.clone();
			checkRemoveFromAbove(op);
			Assert.assertEquals(op, checkRemovable(copyOp));
			removeFromCollection(op);
			break;
		case 10:
			if (theCollection.isEmpty())
				return;
			op = new CollectionOp<>(theCollection.equivalence(), null, helper.getInt(0, modify.size()));
			copyOp = op.clone();
			checkRemoveFromAbove(op);
			Assert.assertEquals(op, checkRemovable(copyOp));
			removeFromCollection(op);
			break;
		case 11: // removeAll
			length = helper.getInt(0, helper.getInt(0, helper.getInt(0, 1000))); // Aggressively tend smaller
			ops = new ArrayList<>(length);
			for (int i = 0; i < length; i++)
				ops.add(new CollectionOp<>(theCollection.equivalence(), theSupplier.apply(helper), -1));
			for (int i = 0; i < length; i++) {
				copyOp = ops.get(i).clone();
				checkRemoveFromAbove(ops.get(i));
				Assert.assertEquals(ops.get(i), checkRemove(copyOp));
			}
			removeAllFromCollection(ops);
			break;
		case 12: // retainAll
			length = helper.getInt(0, 1000);
			BetterSet<E> set=theCollection.equivalence().createSet();
			for(int i=0;i<length;i++)
				set.add(theSupplier.apply(helper));
			ops = new ArrayList<>();
			for(int i=0;i<theCollection.size();i++){
				E value=theCollection.get(i);
				if(!set.contains(value)){
					ops.add(new CollectionOp<>(theCollection.equivalence(), value, i));
				}
			}
			// TODO Wrong
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

	protected abstract CollectionOp<E> checkAddable(CollectionOp<E> op);

	protected abstract CollectionOp<E> checkRemovable(CollectionOp<E> op);

	protected abstract CollectionOp<E> checkSettable(CollectionOp<E> op);

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

	private void removeFromCollection(CollectionOp<E> op) {}

	private void setInCollection(CollectionOp<E> op) {}

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
	public void check() {
		theTester.check();
	}

	@Override
	public ObservableChainLink<?> derive(TestHelper helper) {
		switch (helper.getInt(0, 1)) {
		case 0:
			// TODO mapEquivalent
			theChild = new MappedCollectionLink<>(this, theType, theFlow, helper);
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
	}
}