package org.observe.supertest;

import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class SimpleCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	public SimpleCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper) {
		super(parent, type, flow, helper);
	}

	@Override
	public void checkAddable(CollectionOp<E> add, int subListStart, int subListEnd, TestHelper helper) {
		if (getParent() != null)
			getParent().checkAddable(add, subListStart, subListEnd, helper);
	}

	@Override
	public void checkRemovable(CollectionOp<E> remove, int subListStart, int subListEnd, TestHelper helper) {
		if (getParent() != null)
			getParent().checkRemovable(remove, subListStart, subListEnd, helper);
		else if (remove.index < 0 && !getCollection().contains(remove.source))
			remove.message = StdMsg.NOT_FOUND;
	}

	@Override
	public void checkSettable(CollectionOp<E> set, int subListStart, int subListEnd, TestHelper helper) {
		if (getParent() != null)
			getParent().checkSettable(set, subListStart, subListEnd, helper);
	}

	@Override
	public void addedFromBelow(int index, E value, TestHelper helper) {
		added(index, value, helper, true);
	}

	@Override
	public void removedFromBelow(int index, TestHelper helper) {
		removed(index, helper, true);
	}

	@Override
	public void setFromBelow(int index, E value, TestHelper helper) {
		set(index, value, helper, true);
	}

	@Override
	public void addedFromAbove(int index, E value, TestHelper helper, boolean above) {
		added(index, value, helper, !above);
	}

	@Override
	public void removedFromAbove(int index, E value, TestHelper helper, boolean above) {
		removed(index, helper, !above);
	}

	@Override
	public void setFromAbove(int index, E value, TestHelper helper, boolean above) {
		set(index, value, helper, !above);
	}

	@Override
	public String toString() {
		if (getParent() != null)
			return "simple";
		else
			return "base(" + getType() + ")";
	}
}
