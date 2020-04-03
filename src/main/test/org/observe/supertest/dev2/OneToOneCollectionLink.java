package org.observe.supertest.dev2;

import org.junit.Assert;
import org.observe.collect.CollectionChangeType;
import org.qommons.TestHelper;

public abstract class OneToOneCollectionLink<S, T> extends ObservableCollectionLink<S, T> {
	public OneToOneCollectionLink(ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def, TestHelper helper) {
		super(sourceLink, def, helper);
	}

	protected abstract T map(S sourceValue);

	protected abstract S reverse(T value);

	protected abstract boolean isReversible();

	@Override
	public void initialize(TestHelper helper) {
		super.initialize(helper);
		for (CollectionLinkElement<?, S> sourceEl : getSourceLink().getElements())
			expectAddFromSource(sourceEl);
	}

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
		}
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, int derivedIndex) {
		switch (derivedOp.getType()) {
		case add:
			throw new IllegalStateException("Should be using expectAdd");
		case remove:
			CollectionLinkElement<?, S> sourceEl = (CollectionLinkElement<?, S>) derivedOp.getElement().getSourceElements().getFirst();
			S reversed = isReversible() ? reverse(derivedOp.getElement().get()) : sourceEl.getValue();
			getSourceLink().expect(new ExpectedCollectionOperation<>(//
				sourceEl, CollectionChangeType.remove, reversed, reversed), rejection, getSiblingIndex());
			if (!rejection.isRejected()) {
				derivedOp.getElement().expectRemoval();
				int d = 0;
				for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks()) {
					if (d != derivedIndex)
						derivedLink.expectFromSource(//
							new ExpectedCollectionOperation<>(derivedOp.getElement(), CollectionChangeType.remove,
								derivedOp.getElement().getValue(), derivedOp.getElement().getValue()));
					d++;
				}
			}
			break;
		case set:
			T oldValue = derivedOp.getElement().get();
			getSourceLink().expect(new ExpectedCollectionOperation<>(//
				(CollectionLinkElement<Object, S>) derivedOp.getElement().getSourceElements().getFirst(), CollectionChangeType.set,
				reverse(derivedOp.getElement().get()), reverse(derivedOp.getValue())), rejection, getSiblingIndex());
			if (!rejection.isRejected()) {
				derivedOp.getElement().setValue(derivedOp.getValue());
				int d = 0;
				for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks()) {
					if (d != derivedIndex)
						derivedLink.expectFromSource(//
							new ExpectedCollectionOperation<>(derivedOp.getElement(), CollectionChangeType.set, oldValue,
								derivedOp.getValue()));
					d++;
				}
			}
			break;
		}
	}

	@Override
	public CollectionLinkElement<S, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, int derivedIndex) {
		CollectionLinkElement<S, T> newElement;
		CollectionLinkElement<?, S> sourceEl = getSourceLink().expectAdd(reverse(value), //
			after == null ? null : ((CollectionLinkElement<S, T>) after).getSourceElements().getFirst(), //
				before == null ? null : ((CollectionLinkElement<S, T>) before).getSourceElements().getFirst(), //
					first, rejection, getSiblingIndex());
		if (rejection.isRejected())
			return null;
		newElement = addFromSource(sourceEl, value);
		int d = 0;
		for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks()) {
			if (d != derivedIndex)
				derivedLink.expectFromSource(//
					new ExpectedCollectionOperation<>(newElement, CollectionChangeType.add, null, newElement.getCollectionValue()));
			d++;
		}
		return newElement;
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
		CollectionLinkElement<S, T> newElement = addFromSource(sourceEl, map(sourceEl.get()));
		ExpectedCollectionOperation<S, T> result = new ExpectedCollectionOperation<>(newElement, CollectionChangeType.add, null,
			newElement.get());
		for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks())
			derivedLink.expectFromSource(result);
	}

	private void expectRemoveFromSource(CollectionLinkElement<?, S> sourceEl, S oldSrcValue) {
		CollectionLinkElement<S, T> element = (CollectionLinkElement<S, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst()
			.expectRemoval();
		ExpectedCollectionOperation<S, T> result = new ExpectedCollectionOperation<>(element, CollectionChangeType.remove, element.get(),
			element.get());
		for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks())
			derivedLink.expectFromSource(result);
	}

	private void expectChangeFromSource(CollectionLinkElement<?, S> sourceEl, S oldSrcValue) {
		CollectionLinkElement<S, T> element = (CollectionLinkElement<S, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
		T oldValue = element.get();
		T oldMapped = map(oldSrcValue);
		if (!getCollection().equivalence().elementEquals(oldValue, oldMapped))
			element.error(err -> err.append("Wrong value updated: Expected ").append(oldMapped).append(" but was ").append(oldValue));
		element.setValue(map(sourceEl.get()));
		ExpectedCollectionOperation<S, T> result = new ExpectedCollectionOperation<>(element, CollectionChangeType.set, oldValue,
			element.get());
		for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks())
			derivedLink.expectFromSource(result);
	}
}
