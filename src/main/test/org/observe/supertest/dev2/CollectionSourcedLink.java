package org.observe.supertest.dev2;

import java.util.List;

public interface CollectionSourcedLink<S, T> extends ObservableChainLink<S, T> {
	List<ExpectedCollectionOperation<S, T>> expectFromSource(ExpectedCollectionOperation<?, S> sourceOp);
}
