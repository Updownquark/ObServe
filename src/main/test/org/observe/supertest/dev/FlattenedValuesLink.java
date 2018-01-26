package org.observe.supertest.dev;

import java.util.List;
import java.util.NavigableMap;

import org.observe.SimpleSettableValue;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;

public class FlattenedValuesLink<E, T> extends AbstractObservableCollectionLink<E, T> {
	private final NavigableMap<E, SimpleSettableValue<T>> theBuckets;

	public FlattenedValuesLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, T> flow,
		TestHelper helper, boolean checkRemovedValues, NavigableMap<E, SimpleSettableValue<T>> buckets) {
		super(parent, type, flow, helper, checkRemovedValues);
		theBuckets = buckets;
	}

	@Override
	public void initialize(TestHelper helper) {
		super.initialize(helper);
	}

	@Override
	public void checkModifiable(List<CollectionOp<T>> ops, int subListStart,
		int subListEnd, TestHelper helper) {
		// TODO Auto-generated method stub

	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		// TODO Auto-generated method stub

	}

	@Override
	public void fromAbove(List<CollectionOp<T>> ops, TestHelper helper,
		boolean above) {
		// TODO Auto-generated method stub

	}

	public static <E, T> FlattenedValuesLink<E, T> createFlattenedValuesLink(CollectionDataFlow<?, ?, E> source) {}
}
