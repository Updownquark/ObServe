package org.observe.supertest;

import java.util.List;

import org.observe.collect.ObservableCollection;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
import org.qommons.collect.BetterList;

interface ObservableCollectionChainLink<E, T> extends ObservableChainLink<T> {
	TestValueType getTestType();

	@Override
	ObservableCollectionChainLink<T, ?> getChild();

	ObservableCollection<T> getCollection();

	BetterList<LinkElement> getElements();

	LinkElement getLastAddedOrModifiedElement();

	List<T> getExpected();

	void checkModifiable(List<CollectionOp<T>> ops, int subListStart, int subListEnd, TestHelper helper);

	void fromBelow(List<CollectionOp<E>> ops, TestHelper helper);

	void fromAbove(List<CollectionOp<T>> ops, TestHelper helper, boolean above);
}