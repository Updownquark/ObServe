package org.observe.supertest.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.Assert;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SimpleObservable;
import org.observe.SimpleSettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.ElementId;
import org.qommons.collect.ListenerList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;

import com.google.common.reflect.TypeToken;

public class FlattenedValuesLink<E, T> extends AbstractObservableCollectionLink<E, T> {
	private final BetterSortedMap<E, ValueBucket<T>> theBuckets;
	private final ValueBucket<T> theFloorBucket;

	private final BetterList<E> theSourceValues;
	private final List<ValueBucket<T>> theSourceBuckets;
	private final Map<ValueBucket<T>, List<ElementId>> theBucketSources;

	public FlattenedValuesLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, T> flow,
		TestHelper helper, BetterSortedMap<E, ValueBucket<T>> buckets, ValueBucket<T> floorBucket) {
		super(parent, type, flow, helper, true);
		theBuckets = buckets;
		theFloorBucket = floorBucket;
		theSourceValues = new BetterTreeList<>(false);
		theSourceBuckets = new ArrayList<>();
		theBucketSources = BetterHashMap.build().unsafe().identity().buildMap();
	}

	@Override
	public void initialize(TestHelper helper) {
		super.initialize(helper);
		for (E src : getParent().getCollection()) {
			ElementId srcId = theSourceValues.addElement(src, false).getElementId();
			Map.Entry<E, ValueBucket<T>> bucketEntry = theBuckets.floorEntry(src);
			ValueBucket<T> bucket = bucketEntry != null ? bucketEntry.getValue() : theFloorBucket;
			theSourceBuckets.add(bucket);
			theBucketSources.computeIfAbsent(bucket, b -> new ArrayList<>()).add(srcId);
			getExpected().add(bucket.get());
		}
	}

	@Override
	public void tryModify(TestHelper helper) {
		if (helper.getBoolean(.95))
			super.tryModify(helper);
		else {
			int index = helper.getInt(0, theBuckets.size() + 1);
			ValueBucket<T> bucket = index == theBuckets.size() ? theFloorBucket
				: theBuckets.getEntryById(theBuckets.keySet().getElement(index).getElementId()).getValue();
			T newValue = getSupplier().apply(helper);
			if (helper.isReproducing())
				System.out.println(
					"Setting bucket " + (index == theBuckets.size() ? "floor" : "" + index) + " " + bucket.get() + "->" + newValue);
			bucket.set(newValue, null);
		}
	}

	@Override
	public void checkModifiable(List<CollectionOp<T>> ops, int subListStart, int subListEnd, TestHelper helper) {
		List<CollectionOp<E>> parentOps = new ArrayList<>(ops.size());
		for (CollectionOp<T> op : ops) {
			switch (op.type) {
			case add:
				op.reject(StdMsg.UNSUPPORTED_OPERATION, true);
				break;
			case remove:
				parentOps.add(new CollectionOp<>(op, op.type, theSourceValues.get(op.index), op.index));
				break;
			case set:
				break; // Always permitted, doesn't affect the parent
			}
		}
		getParent().checkModifiable(parentOps, subListStart, subListEnd, helper);
	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		List<CollectionOp<T>> flattenedOps = new ArrayList<>();
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				ElementId srcId = theSourceValues.addElement(op.index, op.value).getElementId();
				Map.Entry<E, ValueBucket<T>> bucketEntry = theBuckets.floorEntry(op.value);
				ValueBucket<T> bucket = bucketEntry != null ? bucketEntry.getValue() : theFloorBucket;
				theSourceBuckets.add(op.index, bucket);
				theBucketSources.computeIfAbsent(bucket, b -> new ArrayList<>()).add(srcId);
				flattenedOps.add(new CollectionOp<>(op.type, bucket.get(), op.index));
				break;
			case remove:
				srcId = theSourceValues.getElement(op.index).getElementId();
				bucket = theSourceBuckets.remove(op.index);
				List<ElementId> bucketSources = theBucketSources.get(bucket);
				bucketSources.remove(srcId);
				if (bucketSources.isEmpty())
					Assert.assertEquals(0, bucket.theSubscriptionCount);
				theSourceValues.mutableElement(srcId).remove();
				flattenedOps.add(new CollectionOp<>(op.type, bucket.get(), op.index));
				break;
			case set:
				srcId = theSourceValues.getElement(op.index).getElementId();
				theSourceValues.mutableElement(srcId).set(op.value);
				bucketEntry = theBuckets.floorEntry(op.value);
				bucket = bucketEntry != null ? bucketEntry.getValue() : theFloorBucket;
				ValueBucket<T> oldBucket = theSourceBuckets.set(op.index, bucket);
				bucketSources = theBucketSources.get(oldBucket);
				bucketSources.remove(srcId);
				if (bucketSources.isEmpty())
					Assert.assertEquals(0, oldBucket.theSubscriptionCount);
				theSourceBuckets.set(op.index, bucket);
				theBucketSources.computeIfAbsent(bucket, b -> new ArrayList<>()).add(srcId);
				flattenedOps.add(new CollectionOp<>(op.type, bucket.get(), op.index));
				break;
			}
		}
		modified(flattenedOps, helper, true);
	}

	@Override
	public void fromAbove(List<CollectionOp<T>> ops, TestHelper helper, boolean above) {
		List<CollectionOp<E>> parentOps = new ArrayList<>();
		List<CollectionOp<T>> flattenedOps = new ArrayList<>();
		List<CollectionOp<T>> childOps = new ArrayList<>();
		for (CollectionOp<T> op : ops) {
			switch (op.type) {
			case add:
				// Unsupported. Shouldn't ever happen.
			case remove:
				ElementId srcId = theSourceValues.getElement(op.index).getElementId();
				parentOps.add(new CollectionOp<>(op.type, theSourceValues.getElement(srcId).get(), op.index));
				flattenedOps.add(op);
				ValueBucket<T> bucket = theSourceBuckets.remove(op.index);
				List<ElementId> bucketSources = theBucketSources.get(bucket);
				bucketSources.remove(srcId);
				if (bucketSources.isEmpty())
					Assert.assertEquals(0, bucket.theSubscriptionCount);
				theSourceValues.mutableElement(srcId).remove();
				break;
			case set:
				bucket = theSourceBuckets.get(op.index);
				for (int i = 0; i < theSourceBuckets.size(); i++) {
					if (theSourceBuckets.get(i) != bucket)
						continue;
					flattenedOps.add(new CollectionOp<>(op.type, op.value, i));
				}
				break;
			}
		}
		modified(flattenedOps, helper, !above);
		getParent().fromAbove(parentOps, helper, true);
	}

	@Override
	public String toString() {
		return "flattenedValues(" + theFloorBucket + ", " + theBuckets + getExtras() + ")";
	}

	public static <E, T> FlattenedValuesLink<E, T> createFlattenedValuesLink(ObservableCollectionChainLink<?, E> parent,
		CollectionDataFlow<?, ?, E> source, TestHelper helper) {
		TestValueType newType = ObservableChainTester.nextType(helper);
		TypeToken<T> t = (TypeToken<T>) newType.getType();
		Function<TestHelper, E> oldSupplier = (Function<TestHelper, E>) ObservableChainTester.SUPPLIERS.get(parent.getTestType());
		Function<TestHelper, T> newSupplier = (Function<TestHelper, T>) ObservableChainTester.SUPPLIERS.get(newType);
		int bucketCount = helper.getInt(3, 10);
		BetterSortedMap<E, ValueBucket<T>> buckets = new BetterTreeMap<>(false, SortedCollectionLink.compare(parent.getTestType(), helper));
		ValueBucket<T> floorBucket = new ValueBucket<>(t);
		floorBucket.set(newSupplier.apply(helper), null);
		for (int b = 0; b < bucketCount; b++) {
			E bucketValue = oldSupplier.apply(helper);
			while (buckets.containsKey(bucketValue)) // No duplicates
				bucketValue = oldSupplier.apply(helper);
			ValueBucket<T> bucket = new ValueBucket<>(t);
			bucket.set(newSupplier.apply(helper), null);
			buckets.put(bucketValue, bucket);
		}
		CollectionDataFlow<?, ?, T> flattenedFlow = source.flattenValues(t, src -> {
			Map.Entry<E, ValueBucket<T>> bucketEntry = buckets.floorEntry(src);
			return bucketEntry != null ? bucketEntry.getValue() : floorBucket;
		});
		return new FlattenedValuesLink<>(parent, newType, flattenedFlow, helper, buckets, floorBucket);
	}

	static class ValueBucket<T> extends SimpleSettableValue<T> {
		private int theSubscriptionCount;

		public ValueBucket(TypeToken<T> type) {
			super(type, false);
		}

		@Override
		protected SimpleObservable<ObservableValueEvent<T>> createEventer(ReentrantReadWriteLock lock,
			Consumer<ListenerList.Builder> listeningOptions) {
			return new ValueBucketEventer();
		}

		@Override
		public String toString() {
			return String.valueOf(get()); // Simpler toString for printing
		}

		class ValueBucketEventer extends SimpleObservable<ObservableValueEvent<T>> {
			@Override
			public Subscription subscribe(Observer<? super ObservableValueEvent<T>> observer) {
				Subscription parentSub = super.subscribe(observer);
				theSubscriptionCount++;
				return () -> {
					theSubscriptionCount--;
					parentSub.unsubscribe();
				};
			}
		}
	}
}
