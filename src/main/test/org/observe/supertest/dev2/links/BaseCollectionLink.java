package org.observe.supertest.dev2.links;

import org.observe.collect.DefaultObservableCollection;
import org.observe.supertest.dev2.ChainLinkGenerator;
import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.CollectionSourcedLink;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableChainLink;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.TestValueType;
import org.qommons.TestHelper;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

public class BaseCollectionLink<T> extends ObservableCollectionLink<T, T> {
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> link) {
			if (link != null)
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			TestValueType type = nextType(helper);

			// Simple tree-backed list
			BetterList<X> backing = new BetterTreeList<>(true);
			DefaultObservableCollection<X> base = new DefaultObservableCollection<>((TypeToken<X>) type.getType(), backing);
			ObservableCollectionTestDef<X> def = new ObservableCollectionTestDef<>(type, base.flow(), base.flow(), true, true);
			return (ObservableChainLink<T, X>) new BaseCollectionLink<>(path, def, helper);
		}
	};

	private int theModSet;
	private int theModificationNumber;
	private int theOverallModification;

	public BaseCollectionLink(String path, ObservableCollectionTestDef<T> def, TestHelper helper) {
		super(path, null, def, helper);
	}

	@Override
	public int getModSet() {
		return theModSet;
	}

	@Override
	public int getModification() {
		return theModificationNumber;
	}

	@Override
	public int getOverallModification() {
		return theOverallModification;
	}

	@Override
	public void setModification(int modSet, int modification, int overallModification) {
		theModSet = modSet;
		theModificationNumber = modification;
		theOverallModification = overallModification;
	}

	@Override
	public void initialize(TestHelper helper) {
		CollectionElement<T> el = getCollection().getTerminalElement(true);
		CollectionLinkElement<T, T> linkEl = getElements().peekFirst();
		while (el != null) {
			linkEl.expectAdded(el.get());
			ExpectedCollectionOperation<T, T> result = new ExpectedCollectionOperation<>(linkEl, CollectionOpType.add, null,
				linkEl.getValue());
			for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks())
				derivedLink.expectFromSource(result);

			el = getCollection().getAdjacentElement(el.getElementId(), true);
			linkEl = CollectionElement.get(getElements().getAdjacentElement(linkEl.getElementAddress(), true));
		}
		super.initialize(helper);
	}

	@Override
	public boolean isAcceptable(T value) {
		return true;
	}

	@Override
	public T getUpdateValue(T value) {
		return value;
	}

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {
		throw new IllegalStateException("Unexpected source");
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		if (!execute)
			return;
		switch (derivedOp.getType()) {
		case add:
		case move:
			throw new IllegalStateException("Should be using expectAdd");
		case remove:
			derivedOp.getElement().expectRemoval();
			for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks()) {
				derivedLink.expectFromSource(//
					new ExpectedCollectionOperation<>(derivedOp.getElement(), CollectionOpType.remove, derivedOp.getElement().getValue(),
						derivedOp.getElement().getValue()));
			}
			break;
		case set:
			T oldValue = derivedOp.getElement().getValue();
			derivedOp.getElement().setValue(derivedOp.getValue());
			for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks()) {
				derivedLink.expectFromSource(//
					new ExpectedCollectionOperation<>(derivedOp.getElement(), CollectionOpType.set, oldValue, derivedOp.getValue()));
			}
			break;
		}
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		for (CollectionElement<CollectionLinkElement<T, T>> el : getElements().elementsBetween(
			after == null ? null : after.getElementAddress(), false, //
				before == null ? null : before.getElementAddress(), false)) {
			if (el.get().wasAdded() && getCollection().equivalence().elementEquals(el.get().getCollectionValue(), value)) {
				el.get().expectAdded(value);
				for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks())
					derivedLink.expectFromSource(//
						new ExpectedCollectionOperation<>(el.get(), CollectionOpType.add, null, el.get().getCollectionValue()));
				return el.get();
			}
		}
		throw new AssertionError("No new elements found to expect between " + after + " and " + before);
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection) {
		if (source.isPresent())
			return (CollectionLinkElement<T, T>) source;
		expect(//
			new ExpectedCollectionOperation<>(source, CollectionOpType.remove, source.getValue(), source.getValue()), rejection, true);
		while (after != null && (after == source || !after.isPresent() || after.wasAdded()))
			after = CollectionElement.get(getElements().getAdjacentElement(after.getElementAddress(), false));
		while (before != null && (before == source || !before.isPresent() || before.wasAdded()))
			before = CollectionElement.get(getElements().getAdjacentElement(before.getElementAddress(), true));
		return expectAdd(//
			source.getValue(), after, before, first, rejection);
	}

	@Override
	protected void validate(CollectionLinkElement<T, T> element) {}

	public static TestValueType nextType(TestHelper helper) {
		// The DOUBLE type is much less performant. There may be some value, but we'll use it less often.
		TestHelper.RandomSupplier<TestValueType> action = helper.createSupplier();
		action.or(10, () -> TestValueType.INT);
		action.or(5, () -> TestValueType.STRING);
		action.or(2, () -> TestValueType.DOUBLE);
		return action.get(null);
	}

	@Override
	public String toString() {
		return "base(" + getType() + ")";
	}
}
