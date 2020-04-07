package org.observe.supertest.dev2.links;

import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev2.ChainLinkGenerator;
import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.ObservableChainLink;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.OneToOneCollectionLink;
import org.qommons.TestHelper;

public class ReversedCollectionLink<T> extends OneToOneCollectionLink<T, T> {
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> link) {
			if (!(link instanceof ObservableCollectionLink))
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			CollectionDataFlow<?, ?, T> oneStepFlow;
			if (helper.getBoolean())
				oneStepFlow = sourceCL.getCollection().reverse().flow();
			else
				oneStepFlow = sourceCL.getCollection().flow().reverse();
			CollectionDataFlow<?, ?, T> multiStepFlow = sourceCL.getDef().multiStepFlow.reverse();
			return (ObservableCollectionLink<T, X>) new ReversedCollectionLink<>(path, sourceCL, new ObservableCollectionTestDef<>(
				sourceCL.getDef().type, oneStepFlow, multiStepFlow, true, sourceCL.getDef().checkOldValues), helper);
		}
	};

	public ReversedCollectionLink(String path, ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper) {
		super(path, sourceLink, def, helper);
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
	protected boolean isReversible() {
		return true;
	}

	@Override
	public boolean isAcceptable(T value) {
		return getSourceLink().isAcceptable(value);
	}

	@Override
	public T getUpdateValue(T value) {
		return getSourceLink().getUpdateValue(value);
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		return super.expectAdd(value, before, after, !first, rejection);
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection) {
		return super.expectMove(source, before, after, !first, rejection);
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
