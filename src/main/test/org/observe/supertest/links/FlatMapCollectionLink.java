package org.observe.supertest.links;

import org.junit.Assert;
import org.observe.collect.ObservableCollection;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.CollectionLinkElement;
import org.observe.supertest.ExpectedCollectionOperation;
import org.observe.supertest.ExpectedCollectionOperation.CollectionOpType;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.ObservableChainTester;
import org.observe.supertest.ObservableCollectionLink;
import org.observe.supertest.ObservableCollectionTestDef;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.qommons.TestHelper;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeMap;

import com.google.common.reflect.TypeToken;

/**
 * Tests {@link org.observe.collect.ObservableCollection.CollectionDataFlow#flatMap(TypeToken, java.util.function.Function) flatMap()}. This
 * tests flat map functionality much more completely than {@link FactoringFlatMapCollectionLink}, but is much more expensive as well.
 *
 * @param <S> The type of the source link
 * @param <T> The type of this link
 */
public class FlatMapCollectionLink<S, T> extends AbstractFlatMappedCollectionLink<S, T> {
	/** Generates {@link FlatMapCollectionLink}s */
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (!(sourceLink instanceof ObservableCollectionLink)
				|| ((ObservableCollectionLink<?, ?>) sourceLink).getValueSupplier() == null)
				return 0;
			return .1; // This link is really heavy--make it less frequent
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			TestValueType type = BaseCollectionLink.nextType(helper);
			BetterSortedMap<T, ObservableCollectionLink<?, X>> buckets = BetterTreeMap
				.<T> build(SortedCollectionLink.compare(sourceCL.getType(), helper))//
				.safe(false).buildMap();
			int bucketCount = helper.getInt(2, 10);
			for (int b = 0; b < bucketCount; b++) {
				T bucketKey = sourceCL.getValueSupplier().apply(helper);
				if (!buckets.containsKey(bucketKey)) {
					ObservableCollectionLink<?, X> bucketLink = ObservableChainTester.generateCollectionLink(type, helper,
						helper.getInt(1, 3));
					buckets.put(bucketKey, bucketLink);
				}
			}
			ObservableCollection.CollectionDataFlow<?, ?, X> oneStepFlow = sourceCL.getCollection().flow()//
				.flatMap((TypeToken<X>) type.getType(),
					s -> FlattenedCollectionValuesLink.getBucket(buckets, s).get().getCollection().flow());
			@SuppressWarnings("cast")
			ObservableCollection.CollectionDataFlow<?, ?, X> multiStepFlow = (ObservableCollection.CollectionDataFlow<?, ?, X>) sourceCL
				.getDef().multiStepFlow//
					.flatMap((TypeToken<X>) type.getType(),
						s -> FlattenedCollectionValuesLink.getBucket(buckets, s).get().getCollection().flow());
			ObservableCollectionTestDef<X> def = new ObservableCollectionTestDef<>(type, oneStepFlow, multiStepFlow,
				sourceCL.getDef().orderImportant, sourceCL.getDef().checkOldValues);
			return new FlatMapCollectionLink<>(path, sourceCL, def, helper, buckets);
		}
	};

	private final BetterSortedMap<S, ObservableCollectionLink<?, T>> theBuckets;

	/**
	 * @param path The path for this link
	 * @param sourceLink The collection source for this link
	 * @param def The collection definition for this link
	 * @param helper The randomness for this link
	 * @param buckets The collection buckets for this link
	 */
	public FlatMapCollectionLink(String path, ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper, BetterSortedMap<S, ObservableCollectionLink<?, T>> buckets) {
		super(path, sourceLink, def, helper);
		theBuckets = buckets;
	}

	/**
	 * @param sourceValue The source value
	 * @return The entry of the bucket to use for the value
	 */
	protected MapEntryHandle<S, ObservableCollectionLink<?, T>> getBucket(S sourceValue) {
		return FlattenedCollectionValuesLink.getBucket(theBuckets, sourceValue);
	}

	@Override
	public CollectionLinkElement<S, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		CollectionLinkElement<?, S> source;
		CollectionLinkElement<?, S> afterSource = after == null ? null : (CollectionLinkElement<?, S>) after.getFirstSource();
		CollectionLinkElement<?, S> beforeSource = before == null ? null : (CollectionLinkElement<?, S>) before.getFirstSource();
		boolean isAfter, isBefore;
		if (first) {
			isBefore = false; // We'll actually determine this at the beginning of the loop
			isAfter = after != null;
			if (after == null)
				source = CollectionElement.get(getSourceLink().getElements().getTerminalElement(true));
			else
				source = (CollectionLinkElement<?, S>) after.getFirstSource();
		} else {
			isAfter = false; // We'll actually determine this at the beginning of the loop
			isBefore = before != null;
			if (before == null)
				source = CollectionElement.get(getSourceLink().getElements().getTerminalElement(false));
			else
				source = (CollectionLinkElement<?, S>) before.getFirstSource();
		}

		String firstReject = null;
		while (source != null) {
			if (first)
				isBefore = beforeSource != null && source == beforeSource;
			else
				isAfter = afterSource != null && source == afterSource;
			boolean isTerminal = first ? isBefore : isAfter;
			ObservableCollectionLink<?, T> bucket = getBucket(source.getValue()).get();
			CollectionLinkElement<?, T> bucketAdded = bucket.expectAdd(value, //
				isAfter ? after.getCustomData() : null, //
					isBefore ? before.getCustomData() : null, //
						first, rejection);
			if (bucketAdded != null) {
				CollectionLinkElement<S, T> added = getElementByBucketEl(source, bucket, bucketAdded);
				// Account for other source elements mapping to the same bucket
				for (CollectionLinkElement<?, S> s : getSourceLink().getElements()) {
					if (s == source || getBucket(s.getValue()).get() != bucket)
						continue;
					CollectionLinkElement<S, T> added2 = getElementByBucketEl(s, bucket, bucketAdded);
					added2.withCustomData(bucketAdded).expectAdded(value);
				}
				return added.withCustomData(bucketAdded).expectAdded(value);
			}
			if (firstReject == null)
				firstReject = rejection.getRejection();
			rejection.reset();
			if (isTerminal)
				break;
			source = CollectionElement.get(getSourceLink().getElements().getAdjacentElement(source.getElementAddress(), first));
		}
		if (firstReject == null)
			firstReject = StdMsg.UNSUPPORTED_OPERATION;
		rejection.reject(firstReject);
		return null;
	}

	CollectionLinkElement<S, T> getElementByBucketEl(CollectionLinkElement<?, S> sourceEl, ObservableCollectionLink<?, T> bucket,
		CollectionLinkElement<?, T> bucketEl) {
		for (CollectionElement<T> el : getCollection().getElementsBySource(//
			bucketEl.getCollectionAddress(), bucket.getCollection())) {
			CollectionLinkElement<S, T> element = getElement(el.getElementId());
			if (element.getFirstSource() == sourceEl)
				return element;
		}
		throw new AssertionError("Expected element for " + bucketEl + " sourced from " + sourceEl);
	}

	@Override
	public CollectionLinkElement<S, T> expectMove(CollectionLinkElement<?, T> element, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection) {
		CollectionLinkElement<?, S> source;
		CollectionLinkElement<?, S> afterSource = after == null ? null : (CollectionLinkElement<?, S>) after.getFirstSource();
		CollectionLinkElement<?, S> beforeSource = before == null ? null : (CollectionLinkElement<?, S>) before.getFirstSource();
		boolean isAfter, isBefore;
		if (first) {
			isBefore = false; // We'll actually determine this at the beginning of the loop
			isAfter = after != null;
			if (after == null)
				source = CollectionElement.get(getSourceLink().getElements().getTerminalElement(true));
			else
				source = (CollectionLinkElement<?, S>) after.getFirstSource();
		} else {
			isAfter = false; // We'll actually determine this at the beginning of the loop
			isBefore = before != null;
			if (before == null)
				source = CollectionElement.get(getSourceLink().getElements().getTerminalElement(false));
			else
				source = (CollectionLinkElement<?, S>) before.getFirstSource();
		}

		ObservableCollectionLink<?, T> sourceBucket = getBucket((S) element.getFirstSource().getValue()).get();
		sourceBucket.expect(
			new ExpectedCollectionOperation<>(element.getCustomData(), CollectionOpType.remove, element.getValue(), element.getValue()),
			rejection, false);
		String canRemove = rejection.getRejection();
		rejection.reset();

		String firstReject = null;
		while (source != null) {
			if (first)
				isBefore = beforeSource != null && source == beforeSource;
			else
				isAfter = afterSource != null && source == afterSource;
			boolean isTerminal = first ? isBefore : isAfter;
			ObservableCollectionLink<?, T> bucket = getBucket(source.getValue()).get();
			if (source == element.getFirstSource()) {
				CollectionLinkElement<?, T> bucketMoved = sourceBucket.expectMove(//
					element.getCustomData(), //
					after == null ? null : after.getCustomData(), before == null ? null : before.getCustomData(), //
						first, rejection);
				if (bucketMoved != null) {
					if (bucketMoved == element.getCustomData())
						return (CollectionLinkElement<S, T>) element; // No-op
					CollectionLinkElement<S, T> moved = getElementByBucketEl(source, bucket, bucketMoved);
					element.expectRemoval();
					moved.withCustomData(bucketMoved).expectAdded(element.getValue());
					// Account for other source elements mapping to the same bucket
					for (CollectionLinkElement<?, S> s : getSourceLink().getElements()) {
						if (s == source || getBucket(s.getValue()).get() != bucket)
							continue;

						boolean found = false;
						for (CollectionLinkElement<S, ?> derived : s.getDerivedElements(getSiblingIndex())) {
							if (!derived.isPresent()
								&& getCollection().equivalence().elementEquals(element.getValue(), derived.getValue())) {
								derived.expectRemoval();
								found = true;
								break;
							}
						}
						if (!found)
							throw new AssertionError("Expected removal of " + element.getValue() + " derived from " + s);
						CollectionLinkElement<S, T> added2 = getElementByBucketEl(s, bucket, bucketMoved);
						added2.withCustomData(bucketMoved).expectAdded(element.getValue());
					}
					return moved;
				}
			} else if (canRemove != null) {
				rejection.reject(canRemove);
			} else {
				CollectionLinkElement<?, T> bucketAdded = bucket.expectAdd(element.getValue(), //
					isAfter ? after.getCustomData() : null, //
						isBefore ? before.getCustomData() : null, //
							first, rejection);
				if (bucketAdded != null) {
					sourceBucket.expect(new ExpectedCollectionOperation<>(element.getCustomData(), CollectionOpType.remove,
						element.getValue(), element.getValue()), rejection, true);
					element.expectRemoval();
					CollectionLinkElement<S, T> added = getElementByBucketEl(source, bucket, bucketAdded);
					// Account for other source elements mapping to the same bucket
					for (CollectionLinkElement<?, S> s : getSourceLink().getElements()) {
						if (s == source)
							continue;
						ObservableCollectionLink<?, T> sBucket = getBucket(s.getValue()).get();
						if (sBucket == sourceBucket) {
							boolean found = false;
							for (CollectionLinkElement<S, ?> derived : s.getDerivedElements(getSiblingIndex())) {
								if (!derived.isPresent()
									&& getCollection().equivalence().elementEquals(element.getValue(), derived.getValue())) {
									derived.expectRemoval();
									found = true;
									break;
								}
							}
							if (!found)
								throw new AssertionError("Expected removal of " + element.getValue() + " derived from " + s);
						} else if (sBucket == bucket) {
							CollectionLinkElement<S, T> added2 = getElementByBucketEl(s, bucket, bucketAdded);
							added2.withCustomData(bucketAdded).expectAdded(element.getValue());
						}
					}
					return added.withCustomData(bucketAdded).expectAdded(element.getValue());
				}
			}

			if (firstReject == null)
				firstReject = rejection.getRejection();
			rejection.reset();
			if (isTerminal)
				break;
			source = CollectionElement.get(getSourceLink().getElements().getAdjacentElement(source.getElementAddress(), first));
		}
		if (firstReject == null)
			firstReject = StdMsg.UNSUPPORTED_OPERATION;
		rejection.reject(firstReject);
		return null;
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		ObservableCollectionLink<?, T> bucket = getBucket((S) derivedOp.getElement().getFirstSource().getValue()).get();
		bucket.expect(new ExpectedCollectionOperation<>(derivedOp.getElement().getCustomData(), derivedOp.getType(),
			derivedOp.getOldValue(), derivedOp.getValue()), rejection, execute);
		if (rejection.isRejected() || !execute)
			return;
		switch (derivedOp.getType()) {
		case add:
		case move:
			throw new IllegalStateException();
		case remove:
			derivedOp.getElement().expectRemoval();
			break;
		case set:
			derivedOp.getElement().expectSet(derivedOp.getValue());
			break;
		}
	}

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, S> sourceOp) {
		switch (sourceOp.getType()) {
		case move:
			throw new IllegalStateException();
		case add:
			ObservableCollectionLink<?, T> bucket = getBucket(sourceOp.getValue()).get();
			BetterList<CollectionLinkElement<S, T>> added;
			added = (BetterList<CollectionLinkElement<S, T>>) (BetterList<?>) sourceOp.getElement().getDerivedElements(getSiblingIndex());
			Assert.assertEquals(bucket.getCollection().size(), added.size());
			for (int i = 0; i < added.size(); i++) {
				CollectionLinkElement<?, T> bucketEl = bucket.getElements().get(i);
				added.get(i).withCustomData(bucketEl).expectAdded(bucketEl.getValue());
			}
			break;
		case remove:
			bucket = getBucket(sourceOp.getValue()).get();
			BetterList<CollectionLinkElement<S, T>> removed;
			removed = (BetterList<CollectionLinkElement<S, T>>) (BetterList<?>) sourceOp.getElement().getDerivedElements(getSiblingIndex());
			Assert.assertEquals(bucket.getCollection().size(), removed.size());
			for (int i = 0; i < removed.size(); i++)
				removed.get(i).expectRemoval();
			break;
		case set:
			if (getSourceLink().getCollection().equivalence().elementEquals(sourceOp.getOldValue(), sourceOp.getValue()))
				return; // No-op
			bucket = getBucket(sourceOp.getOldValue()).get();
			removed = (BetterList<CollectionLinkElement<S, T>>) (BetterList<?>) sourceOp.getElement().getDerivedElements(getSiblingIndex());
			ObservableCollectionLink<?, T> newBucket = getBucket(sourceOp.getValue()).get();
			// For changes where the old and new values map to the same bucket, the collection should just update everything,
			// but if event order messes with this, we'll allow remove and re-add
			if (bucket != newBucket || removed.isEmpty() || !removed.getFirst().isPresent()) {
				// Easy--remove followed by add
				removed = (BetterList<CollectionLinkElement<S, T>>) (BetterList<?>) sourceOp.getElement()
					.getDerivedElements(getSiblingIndex());
				Assert.assertEquals(bucket.getCollection().size(), removed.size());
				for (int i = 0; i < removed.size(); i++)
					removed.get(i).expectRemoval();

				added = (BetterList<CollectionLinkElement<S, T>>) (BetterList<?>) sourceOp.getElement()
					.getDerivedElements(getSiblingIndex());
				Assert.assertEquals(bucket.getCollection().size(), added.size());
				for (int i = 0; i < added.size(); i++) {
					CollectionLinkElement<?, T> bucketEl = bucket.getElements().get(i);
					added.get(i).withCustomData(bucketEl).expectAdded(bucketEl.getValue());
				}
			}
		}
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
