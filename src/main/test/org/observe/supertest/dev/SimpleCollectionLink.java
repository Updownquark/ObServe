package org.observe.supertest.dev;

import java.util.List;

import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class SimpleCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	public SimpleCollectionLink(TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper) {
		super(null, type, flow, helper, false, true);
		for (E src : getCollection())
			getExpected().add(src);
	}

	@Override
	public void checkModifiable(List<CollectionOp<E>> ops, int subListStart, int subListEnd, TestHelper helper) {
		for (CollectionOp<E> op : ops) {
			if (op.type == CollectionChangeType.remove) {
				if (op.index < 0 && !getCollection().contains(op.value))
					op.reject(StdMsg.NOT_FOUND, false);
			}
		}
	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		modified(ops, helper, true);
	}

	@Override
	public void fromAbove(List<CollectionOp<E>> ops, TestHelper helper, boolean above) {
		modified(ops, helper, !above);
	}

	@Override
	public String toString() {
		if (getParent() != null)
			return "simple(" + getExtras() + ")";
		else
			return "base(" + getTestType() + getExtras() + ")";
	}
}
