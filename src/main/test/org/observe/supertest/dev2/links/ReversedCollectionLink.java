package org.observe.supertest.dev2.links;

import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.OneToOneCollectionLink;
import org.qommons.TestHelper;

public class ReversedCollectionLink<T> extends OneToOneCollectionLink<T, T> {
	public ReversedCollectionLink(ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def, TestHelper helper) {
		super(sourceLink, def, helper, SOURCE_ORDERED.andThen(comp -> (node -> -comp.compareTo(node))));
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
		CollectionLinkElement<?, T> sourceEl = theSourceLink.expectAdd(reverse(value), //
			before == null ? null : ((CollectionLinkElement<T, T>) before).getSourceElements().getFirst(), //
				after == null ? null : ((CollectionLinkElement<T, T>) after).getSourceElements().getFirst(), //
			first, rejection);
		newElement = addFromSource(sourceEl);
		if (after != null && newElement.getExpectedAddress().compareTo(after.getExpectedAddress()) < 0)
			throw new IllegalStateException("Added in wrong order");
		if (before != null && newElement.getExpectedAddress().compareTo(before.getExpectedAddress()) > 0)
			throw new IllegalStateException("Added in wrong order");
		return newElement;
	}
}
