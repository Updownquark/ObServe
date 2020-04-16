package org.observe.supertest;

import org.qommons.Transaction;

public interface CollectionSourcedLink<S, T> extends ObservableChainLink<S, T> {
	@Override
	default Transaction lock(boolean write, Object cause) {
		return getSourceLink().getCollection().lock(write, cause);
	}

	@Override
	default Transaction tryLock(boolean write, Object cause) {
		return getSourceLink().getCollection().tryLock(write, cause);
	}

	@Override
	ObservableCollectionLink<?, S> getSourceLink();

	void expectFromSource(ExpectedCollectionOperation<?, S> sourceOp);
}
