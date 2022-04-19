package org.observe.supertest.collect;

import java.util.Comparator;

import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.DefaultObservableSortedSet;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSortedSet;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.CollectionOpType;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.qommons.BiTuple;
import org.qommons.TestHelper;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.SortedTreeList;

import com.google.common.reflect.TypeToken;

/**
 * Tests basic {@link ObservableSortedSet}s and non-distinct sorted {@link ObservableCollection}s
 *
 * @param <T> The type of values in the set
 */
public class SortedBaseCollectionLink<T> extends BaseCollectionLink<T> {
	/** Generates a root {@link SortedBaseCollectionLink} */
	public static final ChainLinkGenerator GENERATE_SORTED = new ChainLinkGenerator.CollectionLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (sourceLink != null)
				return 0;
			return 0.5;
		}

		@Override
		public <T, X> ObservableCollectionLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			TestValueType type = targetType != null ? targetType : nextType(helper);

			// Tree-backed sorted list or set
			Comparator<? super X> compare = SortedCollectionLink.compare(type, helper);
			ObservableCollection<X> base;
			boolean distinct = helper.getBoolean();
			if (distinct) {
				BetterSortedSet<X> backing = BetterTreeSet.<X> buildTreeSet(compare).build();
				base = new DefaultObservableSortedSet<>((TypeToken<X>) type.getType(), backing);
			} else {
				BetterSortedList<X> backing = SortedTreeList.<X> buildTreeList(compare).build();
				base = new DefaultObservableCollection<>((TypeToken<X>) type.getType(), backing);
			}
			ObservableCollectionTestDef<X> def = new ObservableCollectionTestDef<>(type, base.flow(), base.flow(), true, true);
			return (ObservableCollectionLink<T, X>) new SortedBaseCollectionLink<>(path, def, compare, distinct, helper);
		}
	};

	private final SortedLinkHelper<T> theHelper;
	private final BetterSet<T> theDistinctValues;

	/**
	 * @param path The path for this link (generally "root")
	 * @param def The collection definition for this link
	 * @param compare The sorting for the values
	 * @param distinct Whether the collection is an {@link ObservableSortedSet} or just a sorted, non-distinct collection
	 * @param helper The randomness to use to initialize this link
	 */
	public SortedBaseCollectionLink(String path, ObservableCollectionTestDef<T> def, Comparator<? super T> compare, boolean distinct,
		TestHelper helper) {
		super(path, def, helper);
		theHelper = new SortedLinkHelper<>(compare, false);
		if (distinct)
			theDistinctValues = getCollection().equivalence().createSet();
		else
			theDistinctValues = null;
	}

	/**
	 * @param path The path for this link (generally "root")
	 * @param def The collection definition for this link
	 * @param collection The collection
	 * @param compare The sorting for the values
	 * @param helper The randomness to use to initialize this link
	 */
	public SortedBaseCollectionLink(String path, ObservableCollectionTestDef<T> def, ObservableCollection<T> collection,
		Comparator<? super T> compare, TestHelper helper) {
		super(path, def, collection, helper);
		theHelper = new SortedLinkHelper<>(compare, false);
		if (collection instanceof ObservableSortedSet)
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
		boolean first, OperationRejection rejection, boolean execute) {
		BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> afterBefore = theHelper.expectAdd(value, after, before, first,
			rejection);
		if (afterBefore == null)
			return null;
		if (theDistinctValues != null) {
			if ((execute && !theDistinctValues.add(value)) || (!execute & theDistinctValues.contains(value))) {
				rejection.reject(StdMsg.ELEMENT_EXISTS);
				return null;
			}
		}
		after = afterBefore.getValue1();
		before = afterBefore.getValue2();
		return super.expectAdd(value, after, before, first, rejection, execute);
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection, boolean execute) {
		BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> afterBefore = theHelper.expectMove(source, after, before, first,
			rejection);
		if (afterBefore == null)
			return null;
		after = afterBefore.getValue1();
		before = afterBefore.getValue2();
		return super.expectMove(source, after, before, first, rejection, execute);
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		if (!theHelper.expectSet(derivedOp, rejection, getElements()))
			return;
		boolean newValue = derivedOp.getType() == CollectionOpType.set //
			&& theHelper.getCompare().compare(derivedOp.getElement().getValue(), derivedOp.getValue()) != 0//
			&& theDistinctValues != null;
		if (newValue && theDistinctValues.contains(derivedOp.getValue())) {
			rejection.reject(StdMsg.ELEMENT_EXISTS);
			return;
		} else {
			T oldValue = derivedOp.getElement().getValue();
			if (newValue && execute)
				theDistinctValues.add(derivedOp.getValue());
			super.expect(derivedOp, rejection, execute);
			if (theDistinctValues != null) {
				if (rejection.isRejected()) {
					if (newValue)
						theDistinctValues.remove(derivedOp.getValue());
				} else {
					switch (derivedOp.getType()) {
					case add:
					case move:
						throw new IllegalStateException();
					case remove:
						theDistinctValues.remove(oldValue);
						break;
					case set:
						if (newValue)
							theDistinctValues.remove(oldValue);
						break;
					}
				}
			}
		}
	}

	@Override
	protected void validate(CollectionLinkElement<T, T> element, boolean transactionEnd) {
		checkOrder(element);
	}

	private void checkOrder(CollectionLinkElement<T, T> element) {
		theHelper.checkOrder(getElements(), element);
	}

	@Override
	public String toString() {
		return "sortedBase(" + getType() + " " + theHelper.getCompare() + (theDistinctValues == null ? "" : ", distinct") + ")";
	}
}
