package org.observe.supertest.map;

import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.collect.ExpectedCollectionOperation;
import org.qommons.Transaction;

public interface MultiMapSourcedLink<K, V, T> extends ObservableChainLink<V, T> {
	@Override
	default Transaction lock(boolean write, Object cause) {
		return getSourceLink().getMultiMap().lock(write, cause);
	}

	@Override
	default Transaction tryLock(boolean write, Object cause) {
		return getSourceLink().getMultiMap().tryLock(write, cause);
	}

	@Override
	ObservableMultiMapLink<?, K, V> getSourceLink();

	void expectKeyChangeFromSource(ExpectedCollectionOperation<?, K> keyOp);

	void expectValueChangeFromSource(ExpectedMultiMapValueOperation<V, K> keyOp);
}
