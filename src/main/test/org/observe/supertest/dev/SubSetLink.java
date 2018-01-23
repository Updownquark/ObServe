package org.observe.supertest.dev;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.Assert;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.ElementId;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;

public class SubSetLink<E> extends AbstractObservableCollectionLink<E, E> {
	private final Comparator<? super E> theCompare;
	private final E theMin;
	private final boolean isMinIncluded;
	private final E theMax;
	private final boolean isMaxIncluded;

	private final BetterTreeList<E> theSourceValues;
	private final BetterSortedMap<ElementId, E> theIncludedValues;
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

		theSourceValues = new BetterTreeList<>(false);
		theIncludedValues = new BetterTreeMap<>(false, ElementId::compareTo);

		boolean pastMin = theMin == null;
		for (E value : getParent().getCollection()) {
			ElementId srcId = theSourceValues.addElement(value, false).getElementId();
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
				if (comp < 0 || (!isMaxIncluded && comp == 0))
					continue;
			}
			theIncludedValues.put(srcId, value);
			getExpected().add(value);
		}
	}

	private int isValueValid(E value) {
		if (theMin != null) {
			int comp = theCompare.compare(theMin, value);
			if (comp > 0 || (!isMinIncluded && comp == 0))
				return -1;
		}
		if (theMax != null) {
			int comp = theCompare.compare(theMax, value);
			if (comp < 0 || (!isMaxIncluded && comp == 0))
				return 1;
		}
		return 0;
	}

	@Override
	public void checkModifiable(List<CollectionOp<E>> ops, int subListStart, int subListEnd, TestHelper helper) {
		List<CollectionOp<E>> parentOps = new ArrayList<>(ops.size());
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				if (isValueValid(op.value) != 0)
					op.reject(StdMsg.ILLEGAL_ELEMENT, true);
				else
					parentOps.add(op.index < 0 ? op : new CollectionOp<>(op, op.type, op.value, op.index));
				break;
			case remove:
				if (isValueValid(op.value) != 0)
					op.reject(StdMsg.NOT_FOUND, false);
				else
					parentOps.add(op.index < 0 ? op : new CollectionOp<>(op, op.type, op.value, op.index));
				break;
			case set:
				if (isValueValid(op.value) != 0)
					op.reject(StdMsg.ILLEGAL_ELEMENT, true);
				else
					parentOps.add(op.index < 0 ? op : new CollectionOp<>(op, op.type, op.value, op.index));
				break;
			}
		}
		getParent().checkModifiable(parentOps, theStartIndex + subListStart, theStartIndex + subListEnd, helper);
	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		List<CollectionOp<E>> subSetOps = new ArrayList<>(ops.size());
		// We need to update theStartIndex as we go, but we can't actually use it here because of multi-element operations like map switches
		for (CollectionOp<E> op : ops) {
			int valid = isValueValid(op.value);
			switch (op.type) {
			case add:
				ElementId srcId=theSourceValues.addElement(op.index, op.value).getElementId();
				if (valid == 0) {
					int addedAt=theIncludedValues.keySet().getElementsBefore(theIncludedValues.putEntry(srcId, op.value, false).getElementId());
					Assert.assertEquals(addedAt, op.index-theStartIndex);
					subSetOps.add(new CollectionOp<>(op.type, op.value, addedAt));
				} else if (valid < 0)
					theStartIndex++;
				break;
			case remove:
				srcId=theSourceValues.getElement(op.index).getElementId();
				MapEntryHandle<ElementId, E> includedEntry=theIncludedValues.getEntry(srcId);
				Assert.assertEquals(valid == 0, includedEntry != null);
				if(includedEntry!=null){
					int includedIndex=theIncludedValues.keySet().getElementsBefore(includedEntry.getElementId());
					Assert.assertEquals(op.index-theStartIndex, includedIndex);
					Assert.assertEquals(op.value, includedEntry.getValue());
					subSetOps.add(new CollectionOp<>(op.type, op.value, includedIndex));
					theIncludedValues.mutableEntry(includedEntry.getElementId()).remove();
				} else if (valid < 0)
					theStartIndex--;
				theSourceValues.mutableElement(srcId).remove();
				break;
			case set:
				srcId=theSourceValues.getElement(op.index).getElementId();
				int oldValid = isValueValid(theSourceValues.getElement(srcId).get());
				theSourceValues.mutableElement(srcId).set(op.value);
				includedEntry = theIncludedValues.getEntry(srcId);
				Assert.assertEquals(oldValid == 0, includedEntry != null);
				if (valid == 0) {
					if (oldValid == 0) {
						theIncludedValues.mutableEntry(includedEntry.getElementId()).set(op.value);
						subSetOps.add(new CollectionOp<>(op.type, op.value,
							theIncludedValues.keySet().getElementsBefore(includedEntry.getElementId())));
					} else {
						includedEntry = theIncludedValues.putEntry(srcId, op.value, false);
						subSetOps.add(new CollectionOp<>(CollectionChangeType.add, op.value,
							theIncludedValues.keySet().getElementsBefore(includedEntry.getElementId())));
						if (oldValid < 0)
							theStartIndex--;
					}
				} else if (oldValid == 0) {
					subSetOps.add(new CollectionOp<>(CollectionChangeType.remove, includedEntry.getValue(),
						theIncludedValues.keySet().getElementsBefore(includedEntry.getElementId())));
					theIncludedValues.mutableEntry(includedEntry.getElementId()).remove();
					if (valid < 0)
						theStartIndex++;
				} else if (oldValid > 0 && valid < 0)
					theStartIndex++;
			}
		}
		modified(subSetOps, helper, true);
	}

	@Override
	public void fromAbove(List<CollectionOp<E>> ops, TestHelper helper, boolean above) {
		List<CollectionOp<E>> parentOps = new ArrayList<>(ops.size());
		for (CollectionOp<E> op : ops) {
			ElementId srcId;
			int srcIndex = theStartIndex + op.index;
			switch (op.type) {
			case add:
				srcId = theSourceValues.addElement(srcIndex, op.value).getElementId();
				theIncludedValues.put(srcId, op.value);
				parentOps.add(new CollectionOp<>(op.type, op.value, srcIndex));
				break;
			case remove:
				srcId = theSourceValues.getElement(srcIndex).getElementId();
				Assert.assertNotNull(theIncludedValues.remove(srcId));
				parentOps.add(new CollectionOp<>(op.type, op.value, srcIndex));
				theSourceValues.mutableElement(srcId).remove();
				break;
			case set:
				srcId = theSourceValues.getElement(srcIndex).getElementId();
				theSourceValues.mutableElement(srcId).set(op.value);
				Assert.assertNotNull(theIncludedValues.put(srcId, op.value));
				parentOps.add(new CollectionOp<>(op.type, op.value, srcIndex));
				break;
			}
		}
		modified(ops, helper, !above);
		getParent().fromAbove(parentOps, helper, true);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder("subSet");
		if (theMin != null) {
			str.append(isMinIncluded ? '(' : '[').append(theMin);
		} else
			str.append("(?");
		str.append(", ");
		if (theMax != null)
			str.append(theMax).append(isMaxIncluded ? ')' : ']');
		else
			str.append("?)");
		return str.toString();
	}
}
