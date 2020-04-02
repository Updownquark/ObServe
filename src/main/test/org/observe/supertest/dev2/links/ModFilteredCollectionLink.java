package org.observe.supertest.dev2.links;

import org.observe.collect.ObservableCollectionDataFlowImpl;
import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.OneToOneCollectionLink;
import org.qommons.TestHelper;

public class ModFilteredCollectionLink<T> extends OneToOneCollectionLink<T, T> {
	private final ObservableCollectionDataFlowImpl.ModFilterer<T> theFilter;

	public ModFilteredCollectionLink(ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def, TestHelper helper,
		ObservableCollectionDataFlowImpl.ModFilterer<T> filter) {
		super(sourceLink, def, helper);
		theFilter = filter;
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
	public boolean isAcceptable(T value) {
		return getSourceLink().isAcceptable(value);
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection) {
		String msg = null;
		switch (derivedOp.getType()) {
		case add:
			msg = theFilter.canAdd(derivedOp.getValue());
			break;
		case remove:
			msg = theFilter.canRemove(//
				() -> derivedOp.getElement().getValue());
			break;
		case set:
			if (derivedOp.getValue() == derivedOp.getElement().getValue() && theFilter.areUpdatesAllowed())
				msg = null;
			else {
				msg = theFilter.canRemove(//
					() -> derivedOp.getElement().getValue());
				if (msg == null)
					msg = theFilter.canAdd(derivedOp.getValue());
			}
			break;
		}
		if (msg != null)
			rejection.reject(msg, true);
		else
			super.expect(derivedOp, rejection);
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		String msg = theFilter.canAdd(value);
		if (msg != null) {
			rejection.reject(msg, true);
			return null;
		}
		return super.expectAdd(value, after, before, first, rejection);
	}

	@Override
	public String toString(){
		return "modFilter(" + theFilter + ")";
	}
}
