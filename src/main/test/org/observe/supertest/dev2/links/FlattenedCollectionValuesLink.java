package org.observe.supertest.dev2.links;

import java.util.function.Function;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev2.ChainLinkGenerator;
import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.CollectionSourcedLink;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableChainLink;
import org.observe.supertest.dev2.ObservableChainTester;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.TestValueType;
import org.qommons.TestHelper;
import org.qommons.TestHelper.RandomAction;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.MapEntryHandle;
import org.qommons.tree.BetterTreeMap;

import com.google.common.reflect.TypeToken;

public class FlattenedCollectionValuesLink<S, T> extends AbstractMappedCollectionLink<S, T> {
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
			return new FlattenedCollectionValuesLink<>(path, sourceCL, def, helper, sourceCL.getDef().checkOldValues,
				BetterCollections.unmodifiableSortedMap(buckets));
		}
	};

	private final BetterSortedMap<S, SettableValue<T>> theBuckets;

	public FlattenedCollectionValuesLink(String path, ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper, boolean cached, BetterSortedMap<S, SettableValue<T>> buckets) {
		super(path, sourceLink, def, helper, cached);
		theBuckets = buckets;
	}

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
			entry.get().set(newValue, null);
			expectBucketChange(entry, oldValue, newValue, null);
		});
	}

	protected void expectBucketChange(MapEntryHandle<S, SettableValue<T>> entry, T oldValue, T newValue,
		CollectionLinkElement<S, T> first) {
		if (first != null) {
			first.setValue(newValue);
			for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
				derived.expectFromSource(new ExpectedCollectionOperation<>(first, CollectionOpType.set, oldValue, newValue));
		}
		for (CollectionLinkElement<S, T> element : getElements()) {
			if (element == first)
				continue;
			S sourceValue = element.getFirstSource().getValue();
			if (entry.getElementId().equals(getBucket(sourceValue).getElementId())) {
				element.setValue(newValue);
				for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
					derived.expectFromSource(new ExpectedCollectionOperation<>(element, CollectionOpType.set, oldValue, newValue));
			}
		}
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		if (derivedOp.getType() == CollectionOpType.set) {
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
		return "flattenedValues(" + getType() + ")";
	}

	static <S, T> MapEntryHandle<S, SettableValue<T>> getBucket(BetterSortedMap<S, SettableValue<T>> buckets, S sourceValue) {
		return buckets.search(buckets.keySet().searchFor(sourceValue, 0), SortedSearchFilter.PreferLess);
	}
}
