package org.observe.supertest.collect;

import org.junit.Assert;
import org.observe.supertest.CollectionOpType;
import org.observe.supertest.OperationRejection;
import org.qommons.testing.TestHelper;

/**
 * An abstract class for derived {@link ObservableCollectionLink}s that represent each of the source elements and derive their order from
 * the source
 *
 * @param <S> The type of the source link's collection
 * @param <T> The type of this link's collection
 */
public abstract class OneToOneCollectionLink<S, T> extends ObservableCollectionLink<S, T> implements CollectionSourcedLink<S, T> {
	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param def The collection definition for this link
	 * @param helper The randomness to use to initialize this link
	 */
	public OneToOneCollectionLink(String path, ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper) {
		super(path, sourceLink, def, helper);
	}

	/**
	 * @param sourceValue The source value to map
	 * @return The mapped value for this link
	 */
	protected abstract T map(S sourceValue);

	/**
	 * @param value The value for this link
	 * @return The reversed value
	 */
	protected abstract S reverse(T value);

	/** @return Whether this link is capable of producing reverse-mapped source values from locally compatible values */
	protected abstract boolean isReversible();

	@Override
	public ObservableCollectionLink<?, S> getSourceLink() {
		return (ObservableCollectionLink<?, S>) super.getSourceLink();
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
			CollectionLinkElement<?, S> sourceEl = (CollectionLinkElement<?, S>) derivedOp.getElement().getFirstSource();
			S reversed = isReversible() ? reverse(derivedOp.getElement().getValue()) : sourceEl.getValue();
			getSourceLink().expect(new ExpectedCollectionOperation<>(//
				sourceEl, CollectionOpType.remove, reversed, reversed), rejection, execute);
			break;
		case set:
			getSourceLink().expect(new ExpectedCollectionOperation<>(//
				(CollectionLinkElement<Object, S>) derivedOp.getElement().getFirstSource(),
				CollectionOpType.set, reverse(derivedOp.getElement().getValue()),
				reverse(derivedOp.getValue())), rejection, execute);
			break;
		}
	}

	@Override
	public CollectionLinkElement<S, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, boolean execute) {
		CollectionLinkElement<?, S> sourceEl = getSourceLink().expectAdd(reverse(value), //
			after == null ? null : ((CollectionLinkElement<S, T>) after).getFirstSource(), //
				before == null ? null : ((CollectionLinkElement<S, T>) before).getFirstSource(), //
					first, rejection, execute);
		if (sourceEl == null)
			return null;
		return (CollectionLinkElement<S, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
	}

	@Override
	public CollectionLinkElement<S, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection, boolean execute) {
		CollectionLinkElement<?, S> oldSource = (CollectionLinkElement<?, S>) source.getFirstSource();
		CollectionLinkElement<?, S> sourceEl = getSourceLink().expectMove(oldSource, //
			after == null ? null : ((CollectionLinkElement<S, T>) after).getFirstSource(), //
				before == null ? null : ((CollectionLinkElement<S, T>) before).getFirstSource(), //
					first, rejection, execute);
		if (rejection.isRejected())
			return null;
		return (CollectionLinkElement<S, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
	}

	@Override
	protected void validate(CollectionLinkElement<S, T> element, boolean transactionEnd) {
		if (element.isPresent()) {
			Assert.assertEquals(1, element.getSourceElements().size());
			checkOrder(element);
		}
	}

	/**
	 * Checks the ordering of the element for validation
	 *
	 * @param element The element to check
	 */
	protected void checkOrder(CollectionLinkElement<S, T> element) {
		int elIndex = element.getIndex();
		int sourceIndex = element.getFirstSource().getIndex();
		if (elIndex != sourceIndex)
			element.error(err -> err.append("Expected at [").append(sourceIndex).append("] but found at [").append(elIndex).append(']'));
	}

	private void expectAddFromSource(CollectionLinkElement<?, S> sourceEl) {
		T value = map(sourceEl.getValue());
		CollectionLinkElement<S, T> newElement = (CollectionLinkElement<S, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
		newElement.expectAdded(value);
	}

	private void expectRemoveFromSource(CollectionLinkElement<?, S> sourceEl, S oldSrcValue) {
		sourceEl.getDerivedElements(getSiblingIndex()).getFirst().expectRemoval();
	}

	private void expectChangeFromSource(CollectionLinkElement<?, S> sourceEl, S oldSrcValue) {
		CollectionLinkElement<S, T> element = (CollectionLinkElement<S, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
		element.expectSet(map(sourceEl.getValue()));
	}
}
