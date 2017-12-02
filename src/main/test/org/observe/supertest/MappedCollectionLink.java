package org.observe.supertest;

import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.observe.supertest.ObservableChainTester.TypeTransformation;
import org.qommons.TestHelper;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class MappedCollectionLink<E, T> extends AbstractObservableCollectionLink<E, T> {
	private final TypeTransformation<E, T> theMap;
	private final FlowOptions.MapDef<E, T> theOptions;

	public MappedCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, T> flow,
		TestHelper helper, TypeTransformation<E, T> map, FlowOptions.MapDef<E, T> options) {
		super(parent, type, flow, helper);
		theMap = map;
		theOptions = options;
	}

	@Override
	public void checkAddable(CollectionOp<T> add, int subListStart, int subListEnd, TestHelper helper) {
		if (theOptions.getReverse() == null) {
			add.message = StdMsg.UNSUPPORTED_OPERATION;
			add.isError = true;
			return;
		}
		E reversed = theOptions.getReverse().apply(add.source);
		if (!getCollection().equivalence().elementEquals(theMap.map(reversed), add.source)) {
			add.message = StdMsg.ILLEGAL_ELEMENT;
			add.isError = true;
			return;
		}
		CollectionOp<E> sourceAdd = new CollectionOp<>(reversed, add.index);
		getParent().checkAddable(sourceAdd, subListStart, subListEnd, helper);
		add.message = sourceAdd.message;
		add.isError = sourceAdd.isError;
	}

	@Override
	public void checkRemovable(CollectionOp<T> remove, int subListStart, int subListEnd, TestHelper helper) {
		if (remove.index < 0) {
			if (!getCollection().contains(remove.source)) {
				remove.message = StdMsg.NOT_FOUND;
				return;
			} else if (theOptions.getReverse() == null) {
				remove.message = StdMsg.UNSUPPORTED_OPERATION;
				remove.isError = true;
				return;
			}
			CollectionOp<E> sourceRemove = new CollectionOp<>(theOptions.getReverse().apply(remove.source), remove.index);
			getParent().checkRemovable(sourceRemove, subListStart, subListEnd, helper);
			remove.message = sourceRemove.message;
			remove.isError = sourceRemove.isError;
		} else
			getParent().checkRemovable((CollectionOp<E>) remove, subListStart, subListEnd, helper);
	}

	@Override
	public void checkSettable(CollectionOp<T> set, int subListStart, int subListEnd, TestHelper helper) {
		if (theOptions.getElementReverse() != null) {
			String message = theOptions.getElementReverse().setElement(getParent().getCollection().get(set.index), set.source, false);
			if (message == null)
				return;
			if (theOptions.getReverse() == null) {
				set.message = message;
				set.isError = true;
				return;
			}
		}
		if (theOptions.getReverse() == null) {
			set.message = StdMsg.UNSUPPORTED_OPERATION;
			set.isError = true;
			return;
		}
		E reversed = theOptions.getReverse().apply(set.source);
		if (!getCollection().equivalence().elementEquals(theMap.map(reversed), set.source)) {
			set.message = StdMsg.ILLEGAL_ELEMENT;
			set.isError = true;
			return;
		}
		CollectionOp<E> sourceSet = new CollectionOp<>(reversed, set.index);
		getParent().checkSettable(sourceSet, subListStart, subListEnd, helper);
		set.message = sourceSet.message;
		set.isError = sourceSet.isError;
	}

	@Override
	public void addedFromBelow(int index, E value, TestHelper helper) {
		added(index, theMap.map(value), helper);
	}

	@Override
	public void removedFromBelow(int index, TestHelper helper) {
		removed(index, helper);
	}

	@Override
	public void setFromBelow(int index, E value, TestHelper helper) {
		// TODO Need to cache (if options allow) to detect whether the change is an update, which may not result in an event for some
		// options
		set(index, theMap.map(value), helper);
	}

	@Override
	public void addedFromAbove(int index, T value, TestHelper helper) {
		getParent().addedFromAbove(index, theOptions.getReverse().apply(value), helper);
	}

	@Override
	public void removedFromAbove(int index, T value, TestHelper helper) {
		getParent().removedFromAbove(index, null, helper); // TODO Is null ok here?
	}

	@Override
	public void setFromAbove(int index, T value, TestHelper helper) {
		if (theOptions.getElementReverse() != null) {
			if (theOptions.getElementReverse().setElement(getParent().getCollection().get(index), value, true) == null)
				return;
		}
		getParent().setFromAbove(index, theOptions.getReverse().apply(value), helper);
	}

	@Override
	public String toString() {
		return "mapped(" + theMap + ")";
	}
}
