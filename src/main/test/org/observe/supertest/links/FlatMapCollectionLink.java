package org.observe.supertest.links;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.observe.collect.ObservableCollection;
import org.observe.supertest.AbstractChainLink;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.CollectionLinkElement;
import org.observe.supertest.CollectionSourcedLink;
import org.observe.supertest.ExpectedCollectionOperation;
import org.observe.supertest.ExpectedCollectionOperation.CollectionOpType;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.ObservableChainTester;
import org.observe.supertest.ObservableCollectionLink;
import org.observe.supertest.ObservableCollectionTestDef;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.qommons.TestHelper;
import org.qommons.TestHelper.RandomAction;
import org.qommons.Transactable;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
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
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator.CollectionLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink)
				|| ((ObservableCollectionLink<?, ?>) sourceLink).getValueSupplier() == null)
				return 0;
			return .1; // This link is really heavy--make it less frequent
		}

		@Override
		public <T, X> ObservableCollectionLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			TestValueType type = targetType != null ? targetType : BaseCollectionLink.nextType(helper);
			BetterSortedMap<T, ObservableCollectionLink<?, X>> buckets = BetterTreeMap
				.<T> build(SortedCollectionLink.compare(sourceCL.getType(), helper))//
				.safe(false).buildMap();
			// Having more buckets actually makes this class more performant, since fewer buckets means that additions to this link
			// aren't duplicated more times though the flattened collection, and there are fewer duplicate update events propagated
			int bucketCount = helper.getInt(10, 20);
			for (int b = 0; b < bucketCount; b++) {
				T bucketKey = sourceCL.getValueSupplier().apply(helper);
				if (!buckets.containsKey(bucketKey)) {
					ObservableCollectionLink<?, X> bucketLink = ObservableChainTester.generateCollectionLink(path + ".bucket[" + b + "]",
						type, helper, helper.getInt(1, 3));
					buckets.put(bucketKey, bucketLink);
				}
			}
			ObservableCollection.CollectionDataFlow<?, ?, X> oneStepFlow = sourceCL.getCollection().flow()//
				.flatMap((TypeToken<X>) type.getType(), s -> {
					MapEntryHandle<T, ObservableCollectionLink<?, X>> bucket = FlattenedCollectionValuesLink.getBucket(buckets, s);
					return bucket.get().getCollection().flow();
				});
			ObservableCollection.CollectionDataFlow<?, ?, X> multiStepFlow = sourceCL.getDef().multiStepFlow
				.flatMap((TypeToken<X>) type.getType(),
					s -> FlattenedCollectionValuesLink.getBucket(buckets, s).get().getDef().multiStepFlow);
			boolean orderImportant = sourceCL.getDef().orderImportant;
			boolean checkOldValues = sourceCL.getDef().checkOldValues;
			for (ObservableCollectionLink<?, X> bucket : buckets.values()) {
				orderImportant &= bucket.getDef().orderImportant;
				checkOldValues &= bucket.getDef().checkOldValues;
			}
			ObservableCollectionTestDef<X> def = new ObservableCollectionTestDef<>(type, oneStepFlow, multiStepFlow,
				orderImportant, checkOldValues);
			return new FlatMapCollectionLink<>(path, sourceCL, def, helper, buckets);
		}
	};

	private final BetterSortedMap<S, ObservableCollectionLink<?, T>> theBuckets;
	private final int[] theBucketCounts;
	private final int theDeepBucketCount;
	private boolean isReportExpected;

	private final List<CollectionLinkElement<?, S>> theSourceElementsByAddedTime;

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
		int bucketCount = 0;
		theBucketCounts = new int[buckets.size()];
		int bucketIdx = 0;
		class BucketReporting extends AbstractChainLink<T, T> implements CollectionSourcedLink<T, T> {
			private final ObservableCollectionLink<?, T> theBucket;

			BucketReporting(ObservableChainLink<?, T> bucket, int bucketIndex) {
				super(FlatMapCollectionLink.this.getPath() + ".bucket[" + bucketIndex + "]*", bucket);
				theBucket = getBucket(bucketIndex).get();
			}

			@Override
			public TestValueType getType() {
				return FlatMapCollectionLink.this.getType();
			}

			@Override
			public ObservableCollectionLink<?, T> getSourceLink() {
				return (ObservableCollectionLink<?, T>) super.getSourceLink();
			}

			@Override
			public double getModificationAffinity() {
				return 0;
			}

			@Override
			public void tryModify(RandomAction action, TestHelper h) {}

			@Override
			public void validate(boolean transactionEnd) throws AssertionError {}

			@Override
			public String printValue() {
				return "";
			}

			@Override
			public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {
				if (!isReportExpected)
					return;
				switch (sourceOp.getType()) {
				case move:
					throw new IllegalStateException();
				case add:
					addToBucket(null, theBucket, sourceOp.getElement(), true);
					break;
				case remove:
					removeFromBucket(theBucket, sourceOp.getElement());
					break;
				case set:
					setInBucket(theBucket, sourceOp.getElement());
					break;
				}
			}

			@Override
			public String toString() {
				return "";
			}
		}
		for (ObservableCollectionLink<?, T> bucket : buckets.values()) {
			ObservableCollectionLink<?, ?> b = bucket;
			while (b != null) {
				bucketCount++;
				theBucketCounts[bucketIdx]++;
				b = b.getSourceLink();
			}
			bucket.getDerivedLinks().add(new BucketReporting(bucket, bucketIdx));
			bucketIdx++;
		}
		theDeepBucketCount = bucketCount;
		theSourceElementsByAddedTime = new LinkedList<>();
	}

	/**
	 * @param sourceValue The source value
	 * @return The entry of the bucket to use for the value
	 */
	protected MapEntryHandle<S, ObservableCollectionLink<?, T>> getBucket(S sourceValue) {
		return FlattenedCollectionValuesLink.getBucket(theBuckets, sourceValue);
	}

	/**
	 * @param index The index of the bucket to get
	 * @return The entry of the bucket to use for the value
	 */
	protected MapEntryHandle<S, ObservableCollectionLink<?, T>> getBucket(int index) {
		return theBuckets.getEntryById(theBuckets.keySet().getElement(index).getElementId());
	}

	@Override
	protected Transactable getLocking() {
		return Transactable.combine(theBuckets.values());
	}

	@Override
	public boolean isComposite() {
		Set<ElementId> buckets = new HashSet<>();
		for (CollectionLinkElement<?, S> sourceEl : getSourceLink().getElements()) {
			if (!buckets.add(getBucket(sourceEl.getValue()).getElementId()))
				return true;
		}
		return false;
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
	public double getModificationAffinity() {
		return super.getModificationAffinity() + 10 + getCollection().size() / 5;
	}

	@Override
	public void tryModify(RandomAction action, TestHelper helper) {
		super.tryModify(action, helper);
		action.or(10, () -> {
			// Allow a bucket to mod itself
			int deepBucketIndex = helper.getInt(0, theDeepBucketCount);
			int bucketIndex = 0;
			ObservableCollectionLink<?, ?> bucketLink = null;
			for (; bucketIndex < theBucketCounts.length; bucketIndex++) {
				if (deepBucketIndex < theBucketCounts[bucketIndex]) {
					bucketLink = getBucket(bucketIndex).get();
					for (int i = theBucketCounts[bucketIndex] - 1; i > deepBucketIndex; i--)
						bucketLink = bucketLink.getSourceLink();
				} else
					deepBucketIndex -= theBucketCounts[bucketIndex];
			}
			if (helper.isReproducing()) {
				System.out.print(bucketLink.getPath() + ": ");
				System.out.flush();
			}
			if (bucketLink.getModificationAffinity() == 0) {
				if (helper.isReproducing())
					System.out.println("Unmodifiable");
				return; // Dud
			}

			isReportExpected = true;
			try {
				RandomAction innerAction = helper.createAction();
				bucketLink.tryModify(innerAction, helper);
				innerAction.execute(null);
			} finally {
				isReportExpected = false;
			}
		});
		action.or(getCollection().size() / 5, () -> {
			if (helper.isReproducing())
				System.out.println("Trimming buckets");
			// In order to keep this test case manageable, we need to keep the bucket sizes down
			for (ObservableCollectionLink<?, T> bucket : theBuckets.values()) {
				int maxSize = helper.getInt(0, 3);
				boolean removable = true;
				while (removable && bucket.getCollection().size() > maxSize) {
					int index = helper.getInt(0, bucket.getCollection().size());
					CollectionElement<T> element = bucket.getCollection().getElement(index);
					while (element != null && bucket.getCollection().mutableElement(element.getElementId()).canRemove() != null)
						element = bucket.getCollection().getAdjacentElement(element.getElementId(), true);
					if (element == null) {
						element = bucket.getCollection().getElement(index);
						do {
							element = bucket.getCollection().getAdjacentElement(element.getElementId(), false);
						} while (element != null && bucket.getCollection().mutableElement(element.getElementId()).canRemove() != null);
					}
					if (element != null) {
						CollectionLinkElement<?, T> bucketEl = bucket.getElement(element.getElementId());
						bucket.getCollection().mutableElement(element.getElementId()).remove();
						OperationRejection.Simple rejection = new OperationRejection.Simple();
						bucket.expect(
							new ExpectedCollectionOperation<>(bucketEl, CollectionOpType.remove, bucketEl.getValue(), bucketEl.getValue()),
							rejection, true);
						Assert.assertNull(rejection.getRejection());
						removeFromBucket(bucket, bucketEl);
					} else
						removable = false;
				}
			}
		});
	}

	@Override
	public CollectionLinkElement<S, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, boolean execute) {
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
		MapEntryHandle<S, ObservableCollectionLink<?, T>> acceptedBucket = null;
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
						first, rejection, false);
			if (bucketAdded != null) {
				if (execute)
					bucket.expectAdd(value, //
						isAfter ? after.getCustomData() : null, //
							isBefore ? before.getCustomData() : null, //
								first, rejection, true);
				return addToBucket(source, bucket, bucketAdded, execute);
			}
			if (acceptedBucket == null && !rejection.isRejected())
				acceptedBucket = getBucket(source.getValue());
			if (firstReject == null)
				firstReject = rejection.getRejection();
			rejection.reset();
			if (isTerminal)
				break;
			else if (first)
				isAfter = false;
			else
				isBefore = false;
			source = CollectionElement.get(getSourceLink().getElements().getAdjacentElement(source.getElementAddress(), first));
		}
		if (acceptedBucket != null)
			throw new AssertionError("Add should have been accepted by " + acceptedBucket + ", but no new element was found");
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
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection, boolean execute) {
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
					isAfter ? after.getCustomData() : null, isBefore ? before.getCustomData() : null, //
						first, rejection, execute);
				if (bucketMoved != null) {
					if (bucketMoved == element.getCustomData())
						return (CollectionLinkElement<S, T>) element; // No-op
					removeFromBucket(bucket, element.getCustomData());
					return addToBucket(source, bucket, bucketMoved, execute);
				}
			} else if (bucket == sourceBucket) {
				rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
			} else if (canRemove != null) {
				rejection.reject(canRemove);
			} else {
				CollectionLinkElement<?, T> bucketAdded = bucket.expectAdd(element.getValue(), //
					isAfter ? after.getCustomData() : null, isBefore ? before.getCustomData() : null, first, rejection, execute);
				if (bucketAdded != null) {
					sourceBucket.expect(new ExpectedCollectionOperation<>(element.getCustomData(), CollectionOpType.remove,
						element.getValue(), element.getValue()), rejection, true);
					if (execute)
						removeFromBucket(sourceBucket, element.getCustomData());
					return addToBucket(source, bucket, bucketAdded, execute);
				}
			}

			if (firstReject == null)
				firstReject = rejection.getRejection();
			rejection.reset();
			if (isTerminal)
				break;
			else if (first)
				isAfter = false;
			else
				isBefore = false;
			source = CollectionElement.get(getSourceLink().getElements().getAdjacentElement(source.getElementAddress(), first));
		}
		if (firstReject == null)
			firstReject = StdMsg.UNSUPPORTED_OPERATION;
		rejection.reject(firstReject);
		return null;
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		CollectionLinkElement<?, S> source = (CollectionLinkElement<?, S>) derivedOp.getElement().getFirstSource();
		ObservableCollectionLink<?, T> bucket = getBucket(source.getValue()).get();
		bucket.expect(new ExpectedCollectionOperation<>(derivedOp.getElement().getCustomData(), derivedOp.getType(),
			derivedOp.getOldValue(), derivedOp.getValue()), rejection, execute);
		if (rejection.isRejected() || !execute)
			return;
		switch (derivedOp.getType()) {
		case add:
		case move:
			throw new IllegalStateException();
		case remove:
			// For multi-remove operations, this can be called for elements that were just removed for being in the same bucket as another
			if (!derivedOp.getElement().isRemoveExpected())
				removeFromBucket(bucket, derivedOp.getElement().getCustomData());
			break;
		case set:
			setInBucket(bucket, derivedOp.getElement().getCustomData());
			break;
		}
	}

	private CollectionLinkElement<S, T> addToBucket(CollectionLinkElement<?, S> source, ObservableCollectionLink<?, T> bucket,
		CollectionLinkElement<?, T> bucketAdded, boolean execute) {
		CollectionLinkElement<S, T> added = source == null ? null : getElementByBucketEl(source, bucket, bucketAdded);
		if (!execute)
			return added;
		// Account for other source elements mapping to the same bucket
		for (CollectionLinkElement<?, S> s : theSourceElementsByAddedTime) {
			if (s == source)
				continue;
			ObservableCollectionLink<?, T> sBucket = getBucket(s.getValue()).get();
			if (sBucket == bucket) {
				CollectionLinkElement<S, T> added2 = getElementByBucketEl(s, bucket, bucketAdded);
				added2.withCustomData(bucketAdded).expectAdded(bucketAdded.getValue());
			}
		}
		if (source != null)
			added.withCustomData(bucketAdded).expectAdded(bucketAdded.getValue());
		return added;
	}

	void removeFromBucket(ObservableCollectionLink<?, T> bucket, CollectionLinkElement<?, T> bucketRemoved) {
		// Account for other source elements mapping to the same bucket
		for (CollectionLinkElement<?, S> s : theSourceElementsByAddedTime) {
			ObservableCollectionLink<?, T> sBucket = getBucket(s.getValue()).get();
			if (sBucket == bucket) {
				boolean found = false;
				for (CollectionLinkElement<S, ?> derived : s.getDerivedElements(getSiblingIndex())) {
					if (derived.getCustomData() == bucketRemoved) {
						derived.expectRemoval();
						found = true;
						break;
					}
				}
				if (!found)
					throw new AssertionError("Expected removal of " + bucketRemoved.getValue() + " derived from " + s);
			}
		}
	}

	void setInBucket(ObservableCollectionLink<?, T> bucket, CollectionLinkElement<?, T> bucketSet) {
		// Account for other source elements mapping to the same bucket
		for (CollectionLinkElement<?, S> s : theSourceElementsByAddedTime) {
			ObservableCollectionLink<?, T> sBucket = getBucket(s.getValue()).get();
			if (sBucket == bucket) {
				CollectionLinkElement<S, T> derived = getElementByBucketEl(s, bucket, bucketSet);
				derived.expectSet(bucketSet.getValue());
			}
		}
	}

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, S> sourceOp) {
		switch (sourceOp.getType()) {
		case move:
			throw new IllegalStateException();
		case add:
			theSourceElementsByAddedTime.add(sourceOp.getElement());
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
			theSourceElementsByAddedTime.remove(sourceOp.getElement());
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
			BetterList<CollectionLinkElement<S, T>> elements = (BetterList<CollectionLinkElement<S, T>>) (BetterList<?>) sourceOp
				.getElement().getDerivedElements(getSiblingIndex());
			ObservableCollectionLink<?, T> newBucket = getBucket(sourceOp.getValue()).get();
			if (bucket != newBucket) {
				theSourceElementsByAddedTime.remove(sourceOp.getElement());
				theSourceElementsByAddedTime.add(sourceOp.getElement());
			}
			// For changes where the old and new values map to the same bucket, the collection should just update everything,
			// but if event order messes with this, we'll allow remove and re-add
			if (bucket != newBucket || elements.isEmpty() || !elements.getFirst().isPresent()) {
				// Easy--remove followed by add
				Iterator<CollectionLinkElement<S, T>> elementIter = elements.iterator();
				for (int i = 0; i < bucket.getCollection().size(); i++)
					elementIter.next().expectRemoval();

				for (int i = 0; i < newBucket.getCollection().size(); i++) {
					CollectionLinkElement<?, T> bucketEl = newBucket.getElements().get(i);
					elementIter.next().withCustomData(bucketEl).expectAdded(bucketEl.getValue());
				}
			}
		}
	}

	@Override
	public void validate(boolean transactionEnd) throws AssertionError {
		LinkedList<ObservableCollectionLink<?, ?>> bucketChain = new LinkedList<>();
		for (ObservableCollectionLink<?, T> bucket : theBuckets.values()) {
			bucketChain.clear();
			ObservableCollectionLink<?, ?> bucketLink = bucket;
			while (bucketLink != null) {
				bucketChain.addFirst(bucketLink);
				bucketLink = bucketLink.getSourceLink();
			}
			for (ObservableCollectionLink<?, ?> b : bucketChain) {
				try {
					b.validate(transactionEnd);
				} catch (RuntimeException | Error e) {
					System.err.print("In " + bucket.getPath() + " " + bucket + ": ");
					throw e;
				}
			}
		}

		super.validate(transactionEnd);
	}

	@Override
	public String toString() {
		return "flatMap(" + theBuckets.size() + ' ' + getType() + ")";
	}
}
