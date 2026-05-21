package org.observe.supertest.collect;

import org.observe.supertest.ObservableChainLink;
import org.qommons.Transaction;

/**
 * An {@link ObservableChainLink} whose source is a {@link ObservableCollectionLink}
 *
 * @param <S> The type of the source collection
 * @param <T> The type of this collection
 */
public interface CollectionSourcedLink<S, T> extends ObservableChainLink<S, T> {
	@Override
	default Transaction lock(boolean tryOnly) {
		return getSourceLink().getCollection().lock(tryOnly);
	}

	@Override
	default Transaction lockWrite(boolean tryOnly, Object cause) {
		return getSourceLink().getCollection().lockWrite(tryOnly, cause);
	}

	@Override
	ObservableCollectionLink<?, S> getSourceLink();

	/**
	 * Called when an operation was effected on the source collection
	 *
	 * @param sourceOp The operation that was effected on the source collection
	 */
	void expectFromSource(ExpectedCollectionOperation<?, S> sourceOp);
}
