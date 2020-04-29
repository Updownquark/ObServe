package org.observe.supertest.links;

import org.observe.collect.ObservableCollection;
import org.observe.supertest.CollectionLinkElement;
import org.observe.supertest.ExpectedCollectionOperation;
import org.observe.supertest.ObservableCollectionLink;
import org.observe.supertest.ObservableCollectionTestDef;
import org.observe.supertest.OperationRejection;
import org.qommons.TestHelper;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.MapEntryHandle;

public class FlatMapCollectionLink<S, T> extends ObservableCollectionLink<S, T> {
	private final BetterSortedMap<S, ObservableCollection<T>> theBuckets;

	public FlatMapCollectionLink(String path, ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper, BetterSortedMap<S, ObservableCollection<T>> buckets) {
		super(path, sourceLink, def, helper);
		theBuckets = buckets;
	}

	/**
	 * @param sourceValue The source value
	 * @return The entry of the bucket to use for the value
	 */
	protected MapEntryHandle<S, ObservableCollection<T>> getBucket(S sourceValue) {
		return FlattenedCollectionValuesLink.getBucket(theBuckets, sourceValue);
	}

	@Override
	public CollectionLinkElement<S, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {

		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CollectionLinkElement<S, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		// TODO Auto-generated method stub

	}

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, S> sourceOp) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void validate(CollectionLinkElement<S, T> element) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isAcceptable(T value) {
		return !theBuckets.isEmpty();
	}

	@Override
	public T getUpdateValue(T value) {
		return value;
	}

	@Override
	public String toString() {
		return "flatMap(" + theBuckets.size() + getType() + ")";
	}
}
