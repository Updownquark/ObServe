package org.observe.supertest.links;

import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.CollectionLinkElement;
import org.observe.supertest.CollectionSourcedLink;
import org.observe.supertest.ExpectedCollectionOperation;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.ObservableChainTester;
import org.observe.supertest.ObservableCollectionLink;
import org.observe.supertest.ObservableCollectionTestDef;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.qommons.TestHelper;
import org.qommons.TestHelper.RandomAction;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.MapEntryHandle;
import org.qommons.tree.BetterTreeMap;

import com.google.common.reflect.TypeToken;

/**
 * Tests {@link org.observe.collect.ObservableCollection.CollectionDataFlow#flattenValues(TypeToken, Function)} and, thereby,
 * {@link org.observe.collect.ObservableCollection.CollectionDataFlow#refresh(org.observe.Observable)} and mapped
 * {@link org.observe.collect.FlowOptions.MapOptions#withElementSetting(org.observe.collect.ObservableCollection.ElementSetter) element
 * setting}.
 *
 * @param <S> The type of the source link's collection
 * @param <T> The type of this link's collection
 */
public class FlattenedCollectionValuesLink<S, T> extends AbstractMappedCollectionLink<S, T> {
	/** Generates {@link FlattenedCollectionValuesLink}s */
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			TestValueType type = BaseCollectionLink.nextType(helper);
			int bucketCount = helper.getInt(2, 10);
			TypeToken<X> targetType = (TypeToken<X>) type.getType();
			BetterSortedMap<T, SettableValue<X>> buckets = new BetterTreeMap<>(false,
				SortedCollectionLink.compare(sourceCL.getType(), helper));
			Function<TestHelper, X> bucketValueGen = (Function<TestHelper, X>) ObservableChainTester.SUPPLIERS.get(type);
			for (int i = 0; i < bucketCount; i++) {
				T sourceValue = sourceCL.getValueSupplier().apply(helper);
				buckets.computeIfAbsent(sourceValue, __ -> {
					SettableValue<X> value = SettableValue.build(targetType).safe(false).build();
					value.set(bucketValueGen.apply(helper), null);
					return value;
				});
			}
			CollectionDataFlow<?, ?, X> oneStepFlow = sourceCL.getCollection().flow().flattenValues(targetType,
				sourceVal -> getBucket(buckets, sourceVal).get());
			CollectionDataFlow<?, ?, X> multiStepFlow = sourceCL.getDef().multiStepFlow.flattenValues(targetType,
				sourceVal -> getBucket(buckets, sourceVal).get());
			ObservableCollectionTestDef<X> def = new ObservableCollectionTestDef<>(type, oneStepFlow, multiStepFlow,
				sourceCL.getDef().orderImportant, sourceCL.getDef().checkOldValues);
			return new FlattenedCollectionValuesLink<>(path, sourceCL, def, helper, BetterCollections.unmodifiableSortedMap(buckets));
		}
	};

	private final BetterSortedMap<S, SettableValue<T>> theBuckets;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param def The collection definition for this link
	 * @param helper The randomness to use to initialize this link
	 * @param buckets The bucket values used to produce this collection's values for source values
	 */
	public FlattenedCollectionValuesLink(String path, ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper, BetterSortedMap<S, SettableValue<T>> buckets) {
		super(path, sourceLink, def, helper, sourceLink.getDef().checkOldValues);
		theBuckets = buckets;
	}

	/**
	 * @param sourceValue The source value
	 * @return The entry of the bucket to use for the value
	 */
	protected MapEntryHandle<S, SettableValue<T>> getBucket(S sourceValue) {
		return getBucket(theBuckets, sourceValue);
	}

	@Override
	protected T map(S sourceValue) {
		return getBucket(sourceValue).get().get();
	}

	@Override
	protected S reverse(T value) {
		throw new IllegalStateException();
	}

	@Override
	protected boolean isReversible() {
		return false;
	}

	@Override
	public double getModificationAffinity() {
		return super.getModificationAffinity() + 2;
	}

	@Override
	public void tryModify(RandomAction action, TestHelper helper) {
		super.tryModify(action, helper);
		action.or(2, () -> {
			int targetIndex = helper.getInt(0, theBuckets.size());
			T newValue = getValueSupplier().apply(helper);

			MapEntryHandle<S, SettableValue<T>> entry = theBuckets.getEntryById(theBuckets.keySet().getElement(targetIndex).getElementId());
			T oldValue = entry.get().get();
			if (helper.isReproducing())
				System.out.println("Changing bucket [" + theBuckets.keySet().getElementsBefore(entry.getElementId()) + "]" + entry.getKey()
				+ " " + oldValue + "->" + newValue);
			entry.get().set(newValue, null);
			expectBucketChange(entry, oldValue, newValue, null);
		});
	}

	/**
	 * Called when the value of a bucket is changed
	 *
	 * @param entry The bucket whose value has changed
	 * @param oldValue The previous value in the bucket
	 * @param newValue The new value in the bucket
	 * @param first The element to apply the change to first, if any
	 */
	protected void expectBucketChange(MapEntryHandle<S, SettableValue<T>> entry, T oldValue, T newValue,
		CollectionLinkElement<S, T> first) {
		if (first != null) {
			first.setValue(newValue);
			for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
				derived.expectFromSource(//
					new ExpectedCollectionOperation<>(first, ExpectedCollectionOperation.CollectionOpType.set, oldValue, newValue));
		}
		for (CollectionLinkElement<S, T> element : getElements()) {
			if (element == first)
				continue;
			S sourceValue = element.getFirstSource().getValue();
			if (entry.getElementId().equals(getBucket(sourceValue).getElementId())) {
				element.setValue(newValue);
				for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
					derived.expectFromSource(//
						new ExpectedCollectionOperation<>(element, ExpectedCollectionOperation.CollectionOpType.set, oldValue, newValue));
			}
		}
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		if (derivedOp.getType() == ExpectedCollectionOperation.CollectionOpType.set) {
			if (execute) {
				S sourceVal = ((ExpectedCollectionOperation<S, T>) derivedOp).getElement().getFirstSource().getValue();
				MapEntryHandle<S, SettableValue<T>> bucket = getBucket(sourceVal);
				expectBucketChange(bucket, derivedOp.getElement().getValue(), derivedOp.getValue(),
					(CollectionLinkElement<S, T>) derivedOp.getElement());
			}
		} else
			super.expect(derivedOp, rejection, execute);
	}

	@Override
	public String toString() {
		return "flattenedValues(" + theBuckets.size() + " " + getType() + ")";
	}

	static <S, T> MapEntryHandle<S, T> getBucket(BetterSortedMap<S, T> buckets, S sourceValue) {
		return buckets.search(buckets.keySet().searchFor(sourceValue, 0), SortedSearchFilter.PreferLess);
	}
}
