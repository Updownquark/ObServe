package org.observe.supertest.dev;

import java.util.Comparator;

import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.ElementId;

public class SortedDistinctCollectionLink<E> extends DistinctCollectionLink<E> {
	private final Comparator<? super E> theCompare;

	public SortedDistinctCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper, Comparator<? super E> compare, FlowOptions.GroupingDef options) {
		super(parent, type, flow, helper, options);
		theCompare = compare;
	}

	@Override
	protected BetterSortedMap<E, BetterSortedMap<ElementId, E>> getValues() {
		return (BetterSortedMap<E, BetterSortedMap<ElementId, E>>) super.getValues();
	}

	@Override
	public void checkAddable(CollectionOp<E> add, int subListStart, int subListEnd, TestHelper helper) {
		int todo = todo;// TODO Check for order
		super.checkAddable(add, subListStart, subListEnd, helper);
	}

	@Override
	public void checkRemovable(CollectionOp<E> remove, int subListStart, int subListEnd, TestHelper helper) {
		int todo = todo;// TODO Check for order
		super.checkRemovable(remove, subListStart, subListEnd, helper);
	}

	@Override
	public void checkSettable(CollectionOp<E> set, int subListStart, int subListEnd, TestHelper helper) {
		int todo = todo;// TODO Check for order
		super.checkSettable(set, subListStart, subListEnd, helper);
	}
}
