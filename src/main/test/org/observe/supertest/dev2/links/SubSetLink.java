package org.observe.supertest.dev2.links;

import java.util.Comparator;

import org.observe.collect.ObservableSortedSet;
import org.observe.supertest.dev2.ChainLinkGenerator;
import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.CollectionSourcedLink;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableChainLink;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.qommons.TestHelper;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class SubSetLink<T> extends ObservableCollectionLink<T, T> {
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (((ObservableCollectionLink<?, ?>) sourceLink).getValueSupplier() == null)
				return 0;
			else if (((ObservableCollectionLink<?, ?>) sourceLink).getCollection() instanceof ObservableSortedSet)
				return 1;
			else
				return 0;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			T low, high;
			boolean lowIncluded, highIncluded;
			if (helper.getBoolean(.8)) {
				low = sourceCL.getValueSupplier().apply(helper);
				lowIncluded = helper.getBoolean();
			} else {
				low = null;
				lowIncluded = true;
			}
			if (low == null || helper.getBoolean(.8)) {
				high = sourceCL.getValueSupplier().apply(helper);
				highIncluded = helper.getBoolean();
			} else {
				high = null;
				highIncluded = true;
			}
			ObservableSortedSet<T> oneStep = (ObservableSortedSet<T>) sourceCL.getCollection();
			ObservableSortedSet<T> multiStep = (ObservableSortedSet<T>) sourceCL.getMultiStepCollection();
			if (low != null && high != null && oneStep.comparator().compare(low, high) > 0) {
				T temp = low;
				low = high;
				high = temp;
			}
			if (low != null) {
				if (high != null) {
					oneStep = oneStep.subSet(low, lowIncluded, high, highIncluded);
					multiStep = multiStep.subSet(low, lowIncluded, high, highIncluded);
				} else {
					oneStep = oneStep.tailSet(low, lowIncluded);
					multiStep = multiStep.tailSet(low, lowIncluded);
				}
			} else {
				oneStep = oneStep.headSet(high, highIncluded);
				multiStep = multiStep.headSet(high, highIncluded);
			}
			ObservableCollectionTestDef<T> def = new ObservableCollectionTestDef<>(sourceCL.getType(), oneStep.flow(), multiStep.flow(),
				true, sourceCL.getDef().checkOldValues);
			return (ObservableChainLink<T, X>) new SubSetLink<>(path, sourceCL, def, oneStep, multiStep, helper, low, lowIncluded, high,
				highIncluded);
		}
	};
	private final T theLowBound;
	private final boolean isLowIncluded;
	private final T theHighBound;
	private final boolean isHighIncluded;

	public SubSetLink(String path, ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def,
		ObservableSortedSet<T> oneStepSet, ObservableSortedSet<T> multiStepSet, TestHelper helper, T lowBound, boolean lowIncluded,
		T highBound, boolean highIncluded) {
		super(path, sourceLink, def, oneStepSet, multiStepSet, helper);
		theLowBound = lowBound;
		isLowIncluded = lowIncluded;
		theHighBound = highBound;
		isHighIncluded = highIncluded;
	}

	public Comparator<? super T> comparator() {
		return ((ObservableSortedSet<T>) getCollection()).comparator();
	}

	boolean isInBound(T value) {
		if (theLowBound != null) {
			int comp = comparator().compare(value, theLowBound);
			if (comp < 0 || (comp == 0 && !isLowIncluded))
				return false;
		}
		if (theHighBound != null) {
			int comp = comparator().compare(value, theHighBound);
			if (comp > 0 || (comp == 0 && !isHighIncluded))
				return false;
		}
		return true;
	}

	@Override
	public boolean isAcceptable(T value) {
		return isInBound(value) && getSourceLink().isAcceptable(value);
	}

	@Override
	public T getUpdateValue(T value) {
		return getSourceLink().getUpdateValue(value);
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		if (!isInBound(value)) {
			rejection.reject(StdMsg.ILLEGAL_ELEMENT);
			return null;
		}
		CollectionLinkElement<?, T> sourceEl = getSourceLink().expectAdd(value, //
			after == null ? null : (CollectionLinkElement<?, T>) after.getFirstSource(), //
			before == null ? null : (CollectionLinkElement<?, T>) before.getFirstSource(), first, rejection);
		if (rejection.isRejected())
			return null;
		return (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection) {
		CollectionLinkElement<?, T> sourceEl = getSourceLink().expectMove(//
			(CollectionLinkElement<?, T>) source.getFirstSource(), //
			after == null ? null : (CollectionLinkElement<?, T>) after.getFirstSource(), //
				before == null ? null : (CollectionLinkElement<?, T>) before.getFirstSource(), first, rejection);
		if (rejection.isRejected())
			return null;
		return (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		switch (derivedOp.getType()) {
		case add:
		case move:
			throw new IllegalStateException();
		case remove:
			getSourceLink().expect(//
				new ExpectedCollectionOperation<>((CollectionLinkElement<?, T>) derivedOp.getElement().getFirstSource(),
					derivedOp.getType(), derivedOp.getValue(), derivedOp.getValue()),
				rejection, execute);
			break;
		case set:
			if (!isInBound(derivedOp.getValue())) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT);
				return;
			}
			getSourceLink().expect(//
				new ExpectedCollectionOperation<>((CollectionLinkElement<?, T>) derivedOp.getElement().getFirstSource(),
					derivedOp.getType(), derivedOp.getElement().getValue(), derivedOp.getValue()),
				rejection, execute);
			break;
		}
	}

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {
		switch (sourceOp.getType()) {
		case add:
			if (isInBound(sourceOp.getValue())) {
				CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) sourceOp.getElement()
					.getDerivedElements(getSiblingIndex()).getFirst();
				T oldValue = element.getValue();
				element.expectAdded(sourceOp.getValue());
				for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
					derived.expectFromSource(new ExpectedCollectionOperation<>(element, sourceOp.getType(), oldValue, sourceOp.getValue()));
			}
			break;
		case remove:
			if (isInBound(sourceOp.getElement().getValue())) {
				CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) sourceOp.getElement()
					.getDerivedElements(getSiblingIndex()).getFirst();
				element.expectRemoval();
				for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
					derived.expectFromSource(
						new ExpectedCollectionOperation<>(element, sourceOp.getType(), element.getValue(), sourceOp.getValue()));
			}
			break;
		case set:
			boolean wasContained = isInBound(sourceOp.getOldValue());
			boolean isContained = isInBound(sourceOp.getValue());
			if (wasContained || isContained) {
				CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) sourceOp.getElement()
					.getDerivedElements(getSiblingIndex()).getFirst();
				T oldValue = element.getValue();
				if (wasContained) {
					if (isContained) {
						element.setValue(sourceOp.getValue());
						for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
							derived.expectFromSource(
								new ExpectedCollectionOperation<>(element, sourceOp.getType(), oldValue, sourceOp.getValue()));
					} else {
						element.expectRemoval();
						for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
							derived.expectFromSource(
								new ExpectedCollectionOperation<>(element, CollectionOpType.remove, element.getValue(),
									sourceOp.getValue()));
					}
				} else if (isContained) {
					element.expectAdded(sourceOp.getValue());
					for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
						derived.expectFromSource(
							new ExpectedCollectionOperation<>(element, CollectionOpType.add, oldValue, sourceOp.getValue()));
				}
			}
			break;
		case move:
			break;
		}
	}

	@Override
	protected void validate(CollectionLinkElement<T, T> element) {
		if (!isInBound(element.getValue()))
			element.error("Element is out of bounds");
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder("subSet");
		if (theLowBound != null) {
			if (isLowIncluded)
				str.append('[');
			else
				str.append('(');
			str.append(theLowBound);
		} else
			str.append("[..");
		if (theHighBound != null) {
			if (theLowBound != null)
				str.append(',');
			str.append(theHighBound);
			if (isHighIncluded)
				str.append(']');
			else
				str.append(')');
		} else
			str.append("..]");
		return str.toString();
	}
}
