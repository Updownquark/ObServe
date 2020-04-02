package org.observe.supertest.dev2.links;

import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.OneToOneCollectionLink;
import org.qommons.TestHelper;

public class ReversedCollectionLink<T> extends OneToOneCollectionLink<T, T> {
	public ReversedCollectionLink(ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def, TestHelper helper) {
		super(sourceLink, def, helper);
	}

	@Override
	protected T map(T sourceValue) {
		return sourceValue;
	}

	@Override
	protected T reverse(T value) {
		return value;
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		CollectionLinkElement<T, T> newElement;
		CollectionLinkElement<?, T> sourceEl = getSourceLink().expectAdd(reverse(value), //
			before == null ? null : ((CollectionLinkElement<T, T>) before).getSourceElements().getFirst(), //
			after == null ? null : ((CollectionLinkElement<T, T>) after).getSourceElements().getFirst(), //
			!first, rejection);
		if (rejection.isRejected())
			return null;
		newElement = addFromSource(sourceEl, value);
		return newElement;
	}

	@Override
	protected void checkOrder(CollectionLinkElement<T, T> element) {
		int elIndex = element.getIndex();
		int sourceIndex = getSourceLink().getElements().getElementsAfter(element.getSourceElements().getFirst().getElementAddress());
		if (elIndex != sourceIndex)
			element.error(err -> err.append("Expected at [").append(sourceIndex).append("] but found at [").append(elIndex).append(']'));
	}

	@Override
	public String toString() {
		return "reverse()";
	}
}
