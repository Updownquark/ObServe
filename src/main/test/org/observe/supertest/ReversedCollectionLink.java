package org.observe.supertest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;

public class ReversedCollectionLink<E> extends OneToOneCollectionLink<E, E> {
	private int theSize;

	public ReversedCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper, boolean checkRemovedValues) {
		super(parent, type, flow, helper, checkRemovedValues);
	}

	@Override
	public void initialize(TestHelper helper) {
		super.initialize(helper);
		theSize = getParent().getCollection().size();
		for (E value : getParent().getCollection())
			getExpected().add(0, value);
	}

	@Override
	public void checkModifiable(List<CollectionOp<E>> ops, int subListStart, int subListEnd, TestHelper helper) {
		List<CollectionOp<E>> parentOps = new ArrayList<>(ops.size());
		int parentSLS = theSize - subListEnd;
		int parentSLE = theSize - subListStart;
		IntUnaryOperator convertIndex = index -> {
			if (index < 0)
				return -1;
			return (theSize - (index + subListStart)) - parentSLS;
		};
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				parentOps.add(new CollectionOp<>(op, op.type, convertIndex.applyAsInt(op.index), op.value));
				break;
			case remove:
			case set:
				parentOps.add(new CollectionOp<>(op, op.type, convertIndex.applyAsInt(op.index) - 1, op.value));
				break;
			}
		}
		getParent().checkModifiable(parentOps, parentSLS, parentSLE, helper);
	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		List<CollectionOp<E>> reversedOps = new ArrayList<>();
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				reversedOps.add(new CollectionOp<>(op.type, getDestElement(op.elementId), theSize - op.index, op.value));
				theSize++;
				break;
			case remove:
				reversedOps.add(new CollectionOp<>(op.type, getDestElement(op.elementId), theSize - op.index - 1, op.value));
				theSize--;
				break;
			case set:
				reversedOps.add(new CollectionOp<>(op.type, getDestElement(op.elementId), theSize - op.index - 1, op.value));
				break;
			}
		}
		modified(reversedOps, helper, true);
	}

	@Override
	public void fromAbove(List<CollectionOp<E>> ops, TestHelper helper, boolean above) {
		List<CollectionOp<E>> parentOps = new ArrayList<>();
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				parentOps.add(new CollectionOp<>(op.type, getSourceElement(op.elementId), theSize - op.index, op.value));
				theSize++;
				break;
			case remove:
				parentOps.add(new CollectionOp<>(op.type, getSourceElement(op.elementId), theSize - op.index - 1, op.value));
				theSize--;
				break;
			case set:
				parentOps.add(new CollectionOp<>(op.type, getSourceElement(op.elementId), theSize - op.index - 1, op.value));
				break;
			}
		}
		modified(ops, helper, !above);
		getParent().fromAbove(parentOps, helper, true);
	}

	@Override
	public String toString() {
		return "reversed(" + getExtras() + ")";
	}
}
