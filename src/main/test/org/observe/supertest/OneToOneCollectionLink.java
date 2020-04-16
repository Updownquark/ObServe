package org.observe.supertest;

import org.junit.Assert;
import org.qommons.TestHelper;

public abstract class OneToOneCollectionLink<S, T> extends ObservableCollectionLink<S, T> {
	public OneToOneCollectionLink(String path, ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper) {
		super(path, sourceLink, def, helper);
	}

	protected abstract T map(S sourceValue);

	protected abstract S reverse(T value);

	protected abstract boolean isReversible();

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, S> sourceOp) {
		switch (sourceOp.getType()) {
		case add:
			expectAddFromSource(sourceOp.getElement());
			break;
		case remove:
			expectRemoveFromSource(sourceOp.getElement(), sourceOp.getOldValue());
			break;
		case set:
			expectChangeFromSource(sourceOp.getElement(), sourceOp.getOldValue());
			break;
		case move:
			throw new IllegalStateException();
		}
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		switch (derivedOp.getType()) {
		case add:
		case move:
			throw new IllegalStateException("Should be using expectAdd");
		case remove:
			CollectionLinkElement<?, S> sourceEl = (CollectionLinkElement<?, S>) derivedOp.getElement().getSourceElements().getFirst();
			S reversed = isReversible() ? reverse(derivedOp.getElement().getValue()) : sourceEl.getValue();
			getSourceLink().expect(new ExpectedCollectionOperation<>(//
				sourceEl, CollectionOpType.remove, reversed, reversed), rejection, execute);
			break;
		case set:
			getSourceLink().expect(new ExpectedCollectionOperation<>(//
				(CollectionLinkElement<Object, S>) derivedOp.getElement().getSourceElements().getFirst(), CollectionOpType.set,
				reverse(derivedOp.getElement().getValue()), reverse(derivedOp.getValue())), rejection, execute);
			break;
		}
	}

	@Override
	public CollectionLinkElement<S, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		CollectionLinkElement<?, S> sourceEl = getSourceLink().expectAdd(reverse(value), //
			after == null ? null : ((CollectionLinkElement<S, T>) after).getSourceElements().getFirst(), //
				before == null ? null : ((CollectionLinkElement<S, T>) before).getSourceElements().getFirst(), //
					first, rejection);
		if (rejection.isRejected())
			return null;
		return (CollectionLinkElement<S, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
	}

	@Override
	public CollectionLinkElement<S, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection) {
		CollectionLinkElement<?, S> oldSource = (CollectionLinkElement<?, S>) source.getFirstSource();
		CollectionLinkElement<?, S> sourceEl = getSourceLink().expectMove(oldSource, //
			after == null ? null : ((CollectionLinkElement<S, T>) after).getSourceElements().getFirst(), //
				before == null ? null : ((CollectionLinkElement<S, T>) before).getSourceElements().getFirst(), //
					first, rejection);
		if (rejection.isRejected())
			return null;
		return (CollectionLinkElement<S, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
	}

	@Override
	protected void validate(CollectionLinkElement<S, T> element) {
		if (element.isPresent()) {
			Assert.assertEquals(1, element.getSourceElements().size());
			checkOrder(element);
		}
	}

	protected CollectionLinkElement<S, T> addFromSource(CollectionLinkElement<?, S> sourceEl, T value) {
		CollectionLinkElement<S, T> element = (CollectionLinkElement<S, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
		element.expectAdded(value);
		return element;
	}

	protected void checkOrder(CollectionLinkElement<S, T> element) {
		int elIndex = element.getIndex();
		int sourceIndex = element.getSourceElements().getFirst().getIndex();
		if (elIndex != sourceIndex)
			element.error(err -> err.append("Expected at [").append(sourceIndex).append("] but found at [").append(elIndex).append(']'));
	}

	private void expectAddFromSource(CollectionLinkElement<?, S> sourceEl) {
		CollectionLinkElement<S, T> newElement = addFromSource(sourceEl, map(sourceEl.getValue()));
		ExpectedCollectionOperation<S, T> result = new ExpectedCollectionOperation<>(newElement, CollectionOpType.add, null,
			newElement.getValue());
		for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks())
			derivedLink.expectFromSource(result);
	}

	private void expectRemoveFromSource(CollectionLinkElement<?, S> sourceEl, S oldSrcValue) {
		CollectionLinkElement<S, T> element = (CollectionLinkElement<S, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst()
			.expectRemoval();
		ExpectedCollectionOperation<S, T> result = new ExpectedCollectionOperation<>(element, CollectionOpType.remove,
			element.getValue(), element.getValue());
		for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks())
			derivedLink.expectFromSource(result);
	}

	private void expectChangeFromSource(CollectionLinkElement<?, S> sourceEl, S oldSrcValue) {
		CollectionLinkElement<S, T> element = (CollectionLinkElement<S, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
		T oldValue = element.getValue();
		element.setValue(map(sourceEl.getValue()));
		ExpectedCollectionOperation<S, T> result = new ExpectedCollectionOperation<>(element, CollectionOpType.set, oldValue,
			element.getValue());
		for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks())
			derivedLink.expectFromSource(result);
	}
}
