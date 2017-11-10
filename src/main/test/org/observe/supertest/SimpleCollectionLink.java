package org.observe.supertest;

import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;

public class SimpleCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	public SimpleCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper) {
		super(parent, type, flow, helper);
	}

	@Override
	public void checkAddable(CollectionOp<E> add, TestHelper helper) {
		if (getParent() != null)
			getParent().checkAddable(add, helper);
	}

	@Override
	public void checkRemovable(CollectionOp<E> remove, TestHelper helper) {
		if (getParent() != null)
			getParent().checkRemovable(remove, helper);
	}

	@Override
	public void checkSettable(CollectionOp<E> set, TestHelper helper) {
		if (getParent() != null)
			getParent().checkSettable(set, helper);
	}

	@Override
	public void addedFromBelow(int index, E value, TestHelper helper) {
		added(index, value, helper);
	}

	@Override
	public void removedFromBelow(int index, TestHelper helper) {
		removed(index, helper);
	}

	@Override
	public void setFromBelow(int index, E value, TestHelper helper) {
		set(index, value, helper);
	}

	@Override
	public void addedFromAbove(int index, E value, TestHelper helper) {}

	@Override
	public void removedFromAbove(int index, TestHelper helper) {}

	@Override
	public void setFromAbove(int index, E value, TestHelper helper) {}
}
