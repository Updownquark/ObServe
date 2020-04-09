package org.observe.supertest.dev2.links;

import java.util.Comparator;
import java.util.List;

import org.junit.Assert;
import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev2.ChainLinkGenerator;
import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.CollectionSourcedLink;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableChainLink;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.qommons.BiTuple;
import org.qommons.TestHelper;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;

public class DistinctSortedCollectionLink<T> extends ObservableCollectionLink<T, T> {
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (sourceLink instanceof ObservableCollectionLink)
				return 1;
			return 0;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			Comparator<T> compare = SortedCollectionLink.compare(sourceCL.getDef().type, helper);
			FlowOptions.UniqueOptions options = new FlowOptions.SimpleUniqueOptions(true);
			options.useFirst(// TODO helper.getBoolean()
				false);
			CollectionDataFlow<?, ?, T> oneStepFlow = sourceCL.getCollection().flow();
			CollectionDataFlow<?, ?, T> multiStepFlow = sourceCL.getDef().multiStepFlow;
			oneStepFlow = oneStepFlow.distinctSorted(compare, options.isUseFirst());
			multiStepFlow = multiStepFlow.distinctSorted(compare, options.isUseFirst());
			ObservableCollectionTestDef<T> def = new ObservableCollectionTestDef<>(sourceLink.getType(), oneStepFlow, multiStepFlow, true,
				true);
			DistinctSortedCollectionLink<T> derived = new DistinctSortedCollectionLink<>(path, sourceCL, def, compare, options.isUseFirst(),
				helper);
			return (ObservableChainLink<T, X>) derived;
		}
	};

	private final SortedLinkHelper<T> theHelper;
	private final BetterSortedMap<T, ValueElement> theValues;

	public DistinctSortedCollectionLink(String path, ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def,
		Comparator<? super T> compare, boolean useFirst, TestHelper helper) {
		super(path, sourceLink, def, helper);
		theHelper = new SortedLinkHelper<>(compare, useFirst);
		theValues = new BetterTreeMap<>(false, compare);
	}

	// TODO initial values

	@Override
	public boolean isAcceptable(T value) {
		if (theValues.containsKey(value))
			return false;
		return getSourceLink().isAcceptable(value);
	}

	@Override
	public T getUpdateValue(T value) {
		return getSourceLink().getUpdateValue(value);
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> afterBefore = theHelper.expectAdd(value, after, before, first,
			rejection);
		if (afterBefore == null)
			return null;
		if (theValues.containsKey(value)) {
			rejection.reject(StdMsg.ELEMENT_EXISTS);
			return null;
		}
		after = afterBefore.getValue1();
		before = afterBefore.getValue2();

		CollectionLinkElement<?, T> sourceEl = getSourceLink().expectAdd(value, //
			after == null ? null : (CollectionLinkElement<?, T>) after.getSourceElements().getFirst(),
				before == null ? null : (CollectionLinkElement<?, T>) before.getSourceElements().getFirst(), //
					first, rejection);
		if (rejection.isRejected())
			return null;
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
		theHelper.checkOrder(getElements(), element);
		return element;
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection) {
		theHelper.expectMove(source, after, before, first, rejection);
		return rejection.isRejected() ? null : (CollectionLinkElement<T, T>) source; // Can't do an actual move operation
	}

	private boolean isSetting;

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		if (!theHelper.expectSet(derivedOp, rejection, getElements()))
			return;
		T oldValue = derivedOp.getElement().getValue();
		ValueElement valueEl = theValues.computeIfAbsent(oldValue,
			__ -> new ValueElement((CollectionLinkElement<T, T>) derivedOp.getElement()));
		for (CollectionLinkElement<?, T> sourceEl : valueEl.sourceElements) {
			getSourceLink().expect(
				new ExpectedCollectionOperation<>(sourceEl, derivedOp.getType(), sourceEl.getValue(), derivedOp.getValue()), rejection,
				false);
			if (rejection.isRejected())
				return;
		}
		if (execute) {
			boolean set = derivedOp.getType() == CollectionOpType.set;
			if (set) {
				if (theHelper.getCompare().compare(oldValue, derivedOp.getValue()) != 0 && theValues.containsKey(derivedOp.getValue())) {
					rejection.reject(StdMsg.ELEMENT_EXISTS);
					return;
				}
				isSetting = true;
			}

			// The distinct manager calls the active parent element first, but which one is active involves a lot of state to figure out
			// The order of operations here isn't always important, but it can affect other things
			ElementId activeEl = getCollection().getSourceElements(valueEl.element.getCollectionAddress(), getSourceLink().getCollection())
				.getFirst();
			for (CollectionLinkElement<?, T> sourceEl : valueEl.sourceElements) {
				if (!sourceEl.getCollectionAddress().equals(activeEl))
					continue;
				getSourceLink().expect(
					new ExpectedCollectionOperation<>(sourceEl, derivedOp.getType(), sourceEl.getValue(), derivedOp.getValue()), rejection,
					true);
				if (rejection.isRejected())
					throw new AssertionError("Operation accepted on test, but rejected on execution");
			}
			for (CollectionLinkElement<?, T> sourceEl : valueEl.sourceElements) {
				if (sourceEl.getCollectionAddress().equals(activeEl))
					continue;
				getSourceLink().expect(
					new ExpectedCollectionOperation<>(sourceEl, derivedOp.getType(), sourceEl.getValue(), derivedOp.getValue()),
					rejection, true);
				if (rejection.isRejected())
					throw new AssertionError("Operation accepted on test, but rejected on execution");
			}
			if (set) {
				theValues.remove(oldValue);
				theValues.put(derivedOp.getValue(), valueEl);
				isSetting = false;
				valueEl.element.setValue(derivedOp.getValue());
				for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
					derived.expectFromSource(//
						new ExpectedCollectionOperation<>(valueEl.element, CollectionOpType.set, oldValue, derivedOp.getValue()));
			}
		}
	}

	private int theLastRefreshedModification;

	private void refreshElements() {
		for (CollectionLinkElement<T, T> el : getElements())
			el.updateSourceLinks(false);
	}

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {
		if (isSetting)
			return;
		if (getModification() != theLastRefreshedModification) {
			// There are very unique circumstances which can cause a source element encountered here to not have any
			// derived elements mapped to this link.
			// This is not a bug, but a consequence of the order of eventing and the fact that the distinct manager swallows
			// duplicate values (by design)
			// So we need to do this manually
			theLastRefreshedModification = getModification();
			refreshElements();
		}
		ValueElement valueEl = theValues
			.get(sourceOp.getType() == CollectionOpType.add ? sourceOp.getValue() : sourceOp.getOldValue());
		CollectionLinkElement<T, T> element;
		switch (sourceOp.getType()) {
		case add:
			if (valueEl == null) {
				element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex()).getFirst();
				valueEl = new ValueElement(element);
				theValues.put(sourceOp.getValue(), valueEl);
			} else
				element = valueEl.element;
			boolean add = valueEl.sourceElements.isEmpty();
			valueEl.sourceElements.add(sourceOp.getElement());
			if (add) {
				element.expectAdded(sourceOp.getValue());
				for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
					derived.expectFromSource(new ExpectedCollectionOperation<>(element, CollectionOpType.add, null, sourceOp.getValue()));
			}
			break;
		case remove:
			element = valueEl.element;
			Assert.assertTrue(valueEl.sourceElements.remove(sourceOp.getElement()));
			if (valueEl.sourceElements.isEmpty()) {
				theValues.remove(sourceOp.getValue());
				element.expectRemoval();
				for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
					derived.expectFromSource(
						new ExpectedCollectionOperation<>(element, CollectionOpType.remove, element.getValue(), element.getValue()));
			}
			break;
		case set:
			if (theHelper.getCompare().compare(sourceOp.getOldValue(), sourceOp.getValue()) == 0) {
				element = valueEl.element;
				T oldValue = element.getValue();
				element.setValue(sourceOp.getValue());
				for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
					derived.expectFromSource(//
						new ExpectedCollectionOperation<>(element, CollectionOpType.set, oldValue, sourceOp.getValue()));
			} else {
				BetterList<CollectionLinkElement<T, ?>> derivedEls = sourceOp.getElement().getDerivedElements(getSiblingIndex());
				ValueElement newValueEl;
				T oldValue = valueEl.element.getValue();
				if (theValues.containsKey(sourceOp.getValue())) {
					newValueEl = theValues.get(sourceOp.getValue());
					Assert.assertEquals(2, derivedEls.size());
					Assert.assertEquals(newValueEl.element, derivedEls.getLast());
				} else if (valueEl.sourceElements.size() > 1
					|| theHelper.expectMoveFromSource(sourceOp, getSiblingIndex(), getElements())) {
					Assert.assertEquals(2, derivedEls.size());
					newValueEl = new ValueElement((CollectionLinkElement<T, T>) derivedEls.getLast());
					theValues.put(sourceOp.getValue(), newValueEl);
				} else {
					valueEl.element.setValue(sourceOp.getValue());
					theValues.remove(oldValue);
					theValues.put(sourceOp.getValue(), valueEl);
					for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
						derived.expectFromSource(
							new ExpectedCollectionOperation<>(valueEl.element, CollectionOpType.set, oldValue, sourceOp.getValue()));
					return;
				}

				Assert.assertTrue(valueEl.sourceElements.remove(sourceOp.getElement()));
				if (valueEl.sourceElements.isEmpty()) {
					theValues.remove(oldValue);
					valueEl.element.expectRemoval();
					for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
						derived.expectFromSource(//
							new ExpectedCollectionOperation<>(valueEl.element, CollectionOpType.remove, oldValue, oldValue));
				}
				add = newValueEl.sourceElements.isEmpty();
				newValueEl.sourceElements.add(sourceOp.getElement());
				if (add) {
					newValueEl.element.expectAdded(sourceOp.getValue());
					for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
						derived.expectFromSource(//
							new ExpectedCollectionOperation<>(newValueEl.element, CollectionOpType.add, null, sourceOp.getValue()));
				}
			}
			break;
		case move:
			break;
		}
	}

	@Override
	protected void validate(CollectionLinkElement<T, T> element) {
		theHelper.checkOrder(getElements(), element);
	}

	@Override
	public String toString() {
		return "distinctSorted(" + (theHelper.isUsingFirstSource() ? "first" : "") + ")";
	}

	class ValueElement {
		CollectionLinkElement<T, T> element;
		final List<CollectionLinkElement<?, T>> sourceElements;

		ValueElement(CollectionLinkElement<T, T> element) {
			this.element = element;
			sourceElements = new BetterTreeList<>(false);
		}

		@Override
		public String toString() {
			return sourceElements.toString();
		}
	}
}
