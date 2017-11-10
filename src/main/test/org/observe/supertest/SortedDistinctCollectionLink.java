package org.observe.supertest;

import java.util.Comparator;

import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ObservableChainTester.TestValueType;
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
	public void checkAddable(CollectionOp<E> add, TestHelper helper) {
		// TODO Auto-generated method stub
	}

	@Override
	public void checkRemovable(CollectionOp<E> remove, TestHelper helper) {
		// TODO Auto-generated method stub

	}

	@Override
	public void checkSettable(CollectionOp<E> set, TestHelper helper) {
		// TODO Auto-generated method stub

	}
}
