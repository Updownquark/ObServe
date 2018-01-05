package org.observe.supertest.dev;

import java.util.List;

import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class SimpleCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	public SimpleCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper) {
		super(parent, type, flow, helper, false);
		for (E src : getCollection())
			getExpected().add(src);
	}

	@Override
	public void checkAddable(List<CollectionOp<E>> adds, int subListStart, int subListEnd, TestHelper helper) {
		if (getParent() != null)
			getParent().checkAddable(adds, subListStart, subListEnd, helper);
	}

	@Override
	public void checkRemovable(List<CollectionOp<E>> removes, int subListStart, int subListEnd, TestHelper helper) {
		if (getParent() != null)
			getParent().checkRemovable(removes, subListStart, subListEnd, helper);
		else {
			for (CollectionOp<E> remove : removes)
				if (remove.index < 0 && !getCollection().contains(remove.source))
					remove.reject(StdMsg.NOT_FOUND, false);
		}
	}

	@Override
	public void checkSettable(List<CollectionOp<E>> sets, int subListStart, int subListEnd, TestHelper helper) {
		if (getParent() != null)
			getParent().checkSettable(sets, subListStart, subListEnd, helper);
	}

	@Override
	public void addedFromBelow(List<CollectionOp<E>> adds, TestHelper helper) {
		added(adds, helper, true);
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
	public void addedFromAbove(List<CollectionOp<E>> adds, TestHelper helper, boolean above) {
		added(adds, helper, !above);
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
