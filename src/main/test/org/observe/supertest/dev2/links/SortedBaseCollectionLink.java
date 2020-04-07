package org.observe.supertest.dev2.links;

import java.util.Comparator;

import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.DefaultObservableSortedSet;
import org.observe.collect.ObservableCollection;
import org.observe.supertest.dev2.ChainLinkGenerator;
import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableChainLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.TestValueType;
import org.qommons.BiTuple;
import org.qommons.TestHelper;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.SortedTreeList;

import com.google.common.reflect.TypeToken;

public class SortedBaseCollectionLink<T> extends BaseCollectionLink<T> {
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (sourceLink != null)
				return 0;
			return 0.5;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			TestValueType type = nextType(helper);

			// Tree-backed sorted list or set
			Comparator<? super X> compare = SortedCollectionLink.compare(type, helper);
			ObservableCollection<X> base;
			boolean distinct = helper.getBoolean();
			if (distinct) {
				BetterSortedSet<X> backing = new BetterTreeSet<>(true, compare);
				base = new DefaultObservableSortedSet<>((TypeToken<X>) type.getType(), backing);
			} else {
				BetterSortedList<X> backing = new SortedTreeList<>(true, compare);
				base = new DefaultObservableCollection<>((TypeToken<X>) type.getType(), backing);
			}
			ObservableCollectionTestDef<X> def = new ObservableCollectionTestDef<>(type, base.flow(), base.flow(), true, true);
			return (ObservableChainLink<T, X>) new SortedBaseCollectionLink<>(path, def, compare, distinct, helper);
		}
	};

	private final SortedLinkHelper<T> theHelper;
	private final BetterSet<T> theDistinctValues;

	public SortedBaseCollectionLink(String path, ObservableCollectionTestDef<T> def, Comparator<? super T> compare, boolean distinct,
		TestHelper helper) {
		super(path, def, helper);
		theHelper = new SortedLinkHelper<>(compare);
		if (distinct)
			theDistinctValues = getCollection().equivalence().createSet();
		else
			theDistinctValues = null;
	}

	@Override
	public void initialize(TestHelper helper) {
		super.initialize(helper);
		if (theDistinctValues != null)
			theDistinctValues.addAll(getCollection());
	}

	@Override
	public boolean isAcceptable(T value) {
		return theDistinctValues == null || !theDistinctValues.contains(value);
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> afterBefore = theHelper.expectAdd(value, after, before, first,
			rejection);
		if (afterBefore == null)
			return null;
		if (theDistinctValues != null && !theDistinctValues.add(value)) {
			rejection.reject(StdMsg.ELEMENT_EXISTS, false);
			return null;
		}
		after = afterBefore.getValue1();
		before = afterBefore.getValue2();
		return super.expectAdd(value, after, before, first, rejection);
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection) {
		BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> afterBefore = theHelper.expectMove(source, after, before, first,
			rejection);
		if (afterBefore == null)
			return null;
		after = afterBefore.getValue1();
		before = afterBefore.getValue2();
		return super.expectMove(source, after, before, first, rejection);
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection) {
		if (!theHelper.expectSet(derivedOp, rejection, getElements()))
			return;
		else if (derivedOp.getType() == CollectionOpType.set && theDistinctValues != null && !theDistinctValues.add(derivedOp.getValue()))
			return;
		else {
			T oldValue = derivedOp.getElement().getValue();
			super.expect(derivedOp, rejection);
			if (theDistinctValues != null) {
				if (rejection.isRejected()) {
					if (derivedOp.getType() == CollectionOpType.set)
						theDistinctValues.remove(derivedOp.getValue());
				} else {
					switch (derivedOp.getType()) {
					case add:
					case move:
						throw new IllegalStateException();
					case remove:
					case set:
						theDistinctValues.remove(oldValue);
						break;
					}
				}
			}
		}
	}

	@Override
	protected void validate(CollectionLinkElement<T, T> element) {
		checkOrder(element);
	}

	private void checkOrder(CollectionLinkElement<T, T> element) {
		theHelper.checkOrder(getElements(), element);
	}

	@Override
	public String toString() {
		return "sortedBase(" + getType() + (theDistinctValues == null ? "" : ", distinct") + ")";
	}
}
