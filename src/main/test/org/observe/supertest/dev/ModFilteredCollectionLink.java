package org.observe.supertest.dev;

import java.util.ArrayList;
import java.util.List;

import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;

public class ModFilteredCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	private final ObservableCollectionDataFlowImpl.ModFilterer<E> theFilter;

	public ModFilteredCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper, ObservableCollectionDataFlowImpl.ModFilterer<E> filter, boolean checkRemovedValues) {
		super(parent, type, flow, helper, false, checkRemovedValues);
		theFilter = filter;
	}

	@Override
	public void initialize(TestHelper helper) {
		super.initialize(helper);
		for (E src : getParent().getCollection())
			getExpected().add(src);
	}

	@Override
	public void checkModifiable(List<CollectionOp<E>> ops, int subListStart, int subListEnd, TestHelper helper) {
		List<CollectionOp<E>> parentOps = new ArrayList<>();
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				if (theFilter.getUnmodifiableMessage() != null)
					op.reject(theFilter.getUnmodifiableMessage(), true);
				else if (theFilter.getAddMessage() != null)
					op.reject(theFilter.getAddMessage(), true);
				else if (theFilter.getAddFilter() != null && theFilter.getAddFilter().apply(op.value) != null)
					op.reject(theFilter.getAddFilter().apply(op.value), true);
				else
					parentOps.add(op);
				break;
			case remove:
				if (theFilter.getUnmodifiableMessage() != null)
					op.reject(theFilter.getUnmodifiableMessage(), true);
				else if (theFilter.getRemoveMessage() != null)
					op.reject(theFilter.getRemoveMessage(), true);
				else if (theFilter.getRemoveFilter() != null && theFilter.getRemoveFilter().apply(op.value) != null)
					op.reject(theFilter.getRemoveFilter().apply(op.value), true); // Relying on the modification supplying the value
				else
					parentOps.add(op);
				break;
			case set:
				E oldValue = getExpected().get(subListStart + op.index);
				if (oldValue == op.value) {
					// Updates are treated more leniently, since the content of the collection is not changing
					// Updates can only be prevented explicitly
					if (!theFilter.areUpdatesAllowed() && theFilter.getUnmodifiableMessage() != null)
						op.reject(theFilter.getUnmodifiableMessage(), true);
				} else if (theFilter.getUnmodifiableMessage() != null)
					op.reject(theFilter.getUnmodifiableMessage(), true);
				else if (theFilter.getRemoveMessage() != null)
					op.reject(theFilter.getRemoveMessage(), true);
				else if (theFilter.getAddMessage() != null)
					op.reject(theFilter.getAddMessage(), true);
				else if (theFilter.getRemoveFilter() != null && theFilter.getRemoveFilter().apply(oldValue) != null)
					op.reject(theFilter.getRemoveFilter().apply(oldValue), true);
				else if (theFilter.getAddFilter() != null && theFilter.getAddFilter().apply(op.value) != null)
					op.reject(theFilter.getAddFilter().apply(op.value), true);
				else
					parentOps.add(op);
				break;
			}
		}
		getParent().checkModifiable(parentOps, subListStart, subListEnd, helper);
	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		modified(ops, helper, true);
	}

	@Override
	public void fromAbove(List<CollectionOp<E>> ops, TestHelper helper, boolean above) {
		modified(ops, helper, !above);
		getParent().fromAbove(ops, helper, true);
	}

	@Override
	public String toString() {
		return "mod-filtered(" + theFilter + getExtras() + ")";
	}
}
