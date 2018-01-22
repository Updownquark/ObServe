package org.observe.supertest.dev;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.Assert;
import org.observe.collect.ObservableCollection;
import org.observe.supertest.dev.AbstractObservableCollectionLink;
import org.observe.supertest.dev.ObservableChainTester;
import org.observe.supertest.dev.ObservableCollectionChainLink;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.observe.supertest.dev.ObservableCollectionChainLink.CollectionOp;
import org.qommons.TestHelper;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeSet;

public class SubSetLink<E> extends AbstractObservableCollectionLink<E, E> {
	private final Comparator<? super E> theCompare;
	private final E theMin;
	private final boolean isMinIncluded;
	private final E theMax;
	private final boolean isMaxIncluded;

	private final BetterSortedSet<E> theIncludedValues;
	private int theStartIndex;

	public SubSetLink(ObservableCollectionChainLink<?, E> parent, TestValueType type,
		ObservableCollection.UniqueSortedDataFlow<?, ?, E> flow, TestHelper helper, boolean rebasedFlowRequired, boolean checkRemovedValues,
		E min, boolean includeMin, E max, boolean includeMax) {
		super(parent, type, flow, helper, rebasedFlowRequired, checkRemovedValues);
		theCompare = flow.comparator();
		theMin = min;
		isMinIncluded = includeMin;
		theMax = max;
		isMaxIncluded = includeMax;
		theIncludedValues = new BetterTreeSet<>(false, theCompare);

		boolean pastMin = theMin == null;
		for (E value : getParent().getCollection()) {
			if (!pastMin) {
				int comp = theCompare.compare(theMin, value);
				if (comp > 0 || (!isMinIncluded && comp == 0)) {
					theStartIndex++;
					continue;
				} else
					pastMin = true;
			}
			if (theMax != null) {
				int comp = theCompare.compare(theMax, value);
				if (comp < 0 || (isMaxIncluded && comp == 0))
					break;
			}
			theIncludedValues.add(value);
			getExpected().add(value);
		}
	}

	private boolean isValueValid(E value) {
		if (theMin != null) {
			int comp = theCompare.compare(theMin, value);
			if (comp > 0 || (!isMinIncluded && comp == 0))
				return false;
		}
		if (theMax != null) {
			int comp = theCompare.compare(theMax, value);
			if (comp < 0 || (!isMaxIncluded && comp == 0))
				return false;
		}
		return true;
	}

	@Override
	public void checkModifiable(List<CollectionOp<E>> ops, int subListStart, int subListEnd, TestHelper helper) {
		List<CollectionOp<E>> parentOps = new ArrayList<>(ops.size());
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				if (!isValueValid(op.value))
					op.reject(StdMsg.ILLEGAL_ELEMENT, true);
				else
					parentOps.add(op.index < 0 ? op : new CollectionOp<>(op, op.type, op.value, op.index + theStartIndex));
				break;
			case remove:
				if (!isValueValid(op.value))
					op.reject(StdMsg.NOT_FOUND, false);
				else
					parentOps.add(op.index < 0 ? op : new CollectionOp<>(op, op.type, op.value, op.index + theStartIndex));
				break;
			case set:
				if (!isValueValid(op.value))
					op.reject(StdMsg.ILLEGAL_ELEMENT, true);
				else
					parentOps.add(op.index < 0 ? op : new CollectionOp<>(op, op.type, op.value, op.index + theStartIndex));
				break;
			}
		}
		getParent().checkModifiable(parentOps, theStartIndex + subListStart, theStartIndex + subListEnd, helper);
	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		List<CollectionOp<E>> subSetOps = new ArrayList<>(ops.size());
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				if (isValueValid(op.value)){
					int addedAt=theIncludedValues.getElementsBefore(theIncludedValues.addElement(op.value, false).getElementId());
					Assert.assertEquals(addedAt, op.index-theStartIndex);
					subSetOps.add(new CollectionOp<>(op.type, op.value, addedAt));
				}
				else if (op.index <= theStartIndex)
					theStartIndex++;
				break;
			case remove:
				if (op.index < theStartIndex)
					theStartIndex--;
				else if (op.index < theStartIndex+theIncludedValues.size()){
					Assert.assertEquals(theIncludedValues.remove(op.index-theStartIndex), op.value);
					subSetOps.add(new CollectionOp<>(op.type, op.value, op.index - theStartIndex));
				}
				break;
			case set:
				if (isValueValid(op.value)) {
					if(op.index<theStartIndex){
						int addedAt=theIncludedValues.getElementsBefore(theIncludedValues.addElement(op.value, false).getElementId());
						subSetOps.add(new CollectionOp<>
					}
				} else {}
			}
		}
		modified(subSetOps, helper, true);
	}

	@Override
	public void fromAbove(List<CollectionOp<E>> ops, TestHelper helper, boolean above) {
		List<CollectionOp<E>> subSetOps = new ArrayList<>(ops.size());
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:

				break;
			case remove:
				break;
			case set:
				break;
			}
		}
		modified(subSetOps, helper, !above);
	}
}
