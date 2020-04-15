package org.observe.supertest.dev2.links;

import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.OneToOneCollectionLink;
import org.qommons.TestHelper;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public abstract class AbstractMappedCollectionLink<S, T> extends OneToOneCollectionLink<S, T> {
	private final boolean isCached;

	public AbstractMappedCollectionLink(String path, ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper, boolean cached) {
		super(path, sourceLink, def, helper);
		isCached = cached;
	}

	@Override
	public boolean isAcceptable(T value) {
		if (!isReversible())
			throw new IllegalStateException();
		S reversed;
		try {
			reversed = reverse(value);
		} catch (RuntimeException e) {
			return false;
		}
		return getSourceLink().isAcceptable(reversed);
	}

	@Override
	public T getUpdateValue(T value) {
		if (isCached || !isReversible())
			return value;
		else
			return map(getSourceLink().getUpdateValue(reverse(value)));
	}

	@Override
	public CollectionLinkElement<S, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		if (!isReversible()) {
			rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
			return null;
		} else if (!getCollection().equivalence().elementEquals(map(reverse(value)), value)) {
			rejection.reject(StdMsg.ILLEGAL_ELEMENT);
			return null;
		}
		return super.expectAdd(value, after, before, first, rejection);
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		switch (derivedOp.getType()) {
		case add:
		case move:
			throw new IllegalStateException();
		case remove:
			break;
		case set:
			if (isCached && getCollection().equivalence().elementEquals(derivedOp.getElement().getValue(), derivedOp.getValue())) {
				// Update, re-use the previous source value
				CollectionLinkElement<?, S> sourceEl = (CollectionLinkElement<?, S>) derivedOp.getElement().getSourceElements().getFirst();
				S sourceValue;
				if (isReversible()) {
					// If the mapping is reversible, then the source operation for an update is valid either with the reverse-mapped value
					// or the re-used previous source value. So allow either.
					S reversed = reverse(derivedOp.getValue());
					if (getSourceLink().getCollection().equivalence().elementEquals(sourceEl.getCollectionValue(), reversed))
						sourceValue = reversed;
					else if (getSourceLink().getCollection().equivalence().elementEquals(sourceEl.getCollectionValue(),
						sourceEl.getValue()))
						sourceValue = sourceEl.getValue();
					else {
						sourceEl.error("Reverse operation produced " + sourceEl.getCollectionValue() + ", not " + reversed + " or "
							+ sourceEl.getValue());
						return;
					}
				} else
					sourceValue = sourceEl.getValue();
				getSourceLink().expect(
					new ExpectedCollectionOperation<>(sourceEl, derivedOp.getType(), sourceEl.getValue(), sourceValue), rejection,
					execute);
				return;
			}
			if (!isReversible()) {
				rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
				return;
			}
			S reversed = reverse(derivedOp.getValue());
			T reMapped = map(reversed);
			if (!getCollection().equivalence().elementEquals(reMapped, derivedOp.getValue())) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT);
				return;
			}
			break;
		}
		super.expect(derivedOp, rejection, execute);
	}
}
