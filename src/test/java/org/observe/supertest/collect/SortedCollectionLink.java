package org.observe.supertest.collect;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.qommons.BiTuple;
import org.qommons.LambdaUtils;
import org.qommons.TestHelper;

/**
 * Tests {@link org.observe.collect.ObservableCollection.CollectionDataFlow#sorted(Comparator)}
 *
 * @param <T> The type of values in the collection
 */
public class SortedCollectionLink<T> extends ObservableCollectionLink<T, T> implements CollectionSourcedLink<T, T> {
	/** Generates {@link SortedCollectionLink}s */
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator.CollectionLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			else if (targetType != null && targetType != sourceLink.getType())
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableCollectionLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			Comparator<T> compare = compare(sourceLink.getType(), helper);
			CollectionDataFlow<?, ?, T> derivedOneStepFlow = sourceCL.getCollection().flow();
			CollectionDataFlow<?, ?, T> derivedMultiStepFlow = sourceCL.getDef().multiStepFlow;
			derivedOneStepFlow = derivedOneStepFlow.sorted(compare);
			derivedMultiStepFlow = derivedMultiStepFlow.sorted(compare);
			ObservableCollectionTestDef<T> def = new ObservableCollectionTestDef<>(sourceCL.getType(), derivedOneStepFlow,
				derivedMultiStepFlow, false, sourceCL.getDef().checkOldValues);
			return (ObservableCollectionLink<T, X>) new SortedCollectionLink<>(path, sourceCL, def, compare, helper);
		}
	};

	private final SortedLinkHelper<T> theHelper;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param def The collection definition for this link
	 * @param compare The sorting for the collection
	 * @param helper The randomness to use to initialize this link
	 */
	public SortedCollectionLink(String path, ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def,
		Comparator<? super T> compare, TestHelper helper) {
		super(path, sourceLink, def, helper);
		theHelper = new SortedLinkHelper<>(compare, true);
	}

	@Override
	public ObservableCollectionLink<?, T> getSourceLink() {
		return (ObservableCollectionLink<?, T>) super.getSourceLink();
	}

	@Override
	public boolean isAcceptable(T value) {
		return getSourceLink().isAcceptable(value);
	}

	@Override
	public T getUpdateValue(CollectionLinkElement<T, T> element, T value) {
		return ((ObservableCollectionLink<Object, T>) getSourceLink())
			.getUpdateValue((CollectionLinkElement<Object, T>) element.getFirstSource(), value);
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, boolean execute) {
		BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> afterBefore = theHelper.expectAdd(value, after, before, first,
			rejection);
		if (afterBefore == null)
			return null;

		after = afterBefore.getValue1();
		before = afterBefore.getValue2();

		CollectionLinkElement<?, T> sourceEl = getSourceLink().expectAdd(value, //
			after == null ? null : (CollectionLinkElement<?, T>) after.getFirstSource(),
				before == null ? null : (CollectionLinkElement<?, T>) before.getFirstSource(), //
					first, rejection, execute);
		if (sourceEl == null)
			return null;
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
		checkOrder(element);
		return element;
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection, boolean execute) {
		BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> afterBefore = theHelper.expectMove(source, after, before, first,
			rejection);
		if (afterBefore == null)
			return null;
		after = afterBefore.getValue1();
		before = afterBefore.getValue2();
		CollectionLinkElement<?, T> sourceEl = getSourceLink().expectMove(//
			(CollectionLinkElement<?, T>) source.getFirstSource(), //
			after == null ? null : (CollectionLinkElement<?, T>) after.getFirstSource(),
				before == null ? null : (CollectionLinkElement<?, T>) before.getFirstSource(), //
					first, rejection, execute);
		return sourceEl == null ? null : (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		if (rejection.isRejectable() && !theHelper.expectSet(derivedOp, rejection, getElements()))
			return;
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) derivedOp.getElement();
		CollectionLinkElement<?, T> sourceEl = element.getFirstSource();
		getSourceLink().expect(new ExpectedCollectionOperation<>(//
			sourceEl, derivedOp.getType(), derivedOp.getOldValue(), derivedOp.getValue()), rejection, execute);
	}

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex())
			.getFirst();
		switch (sourceOp.getType()) {
		case add:
			element.expectAdded(sourceOp.getValue());
			checkOrder(element);
			break;
		case remove:
			element.expectRemoval();
			break;
		case set:
			// Order of events can determine whether an element needs to be moved or not,
			// so we'll accommodate if it's moved unexpectedly
			if (element.getFirstSource().getDerivedElements(getSiblingIndex()).size() > 1) {
				// This can happen multiple times in some cases, e.g. because of flattened values and distinctness
				Assert.assertTrue(!element.isPresent());
				if (!element.isRemoveExpected())
					element.expectRemoval();
				element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex()).getLast();
				Assert.assertTrue(element.wasAdded());
				if (!element.isAddExpected())
					element.expectAdded(sourceOp.getValue());
				else
					Assert.assertTrue(getCollection().equivalence().elementEquals(sourceOp.getValue(), element.getValue()));
				return;
			} else {
				if (theHelper.expectMoveFromSource(sourceOp, getSiblingIndex(), getElements()))
					throw new AssertionError("Expected move");
				element.expectSet(sourceOp.getValue());
			}
			break;
		case move:
			throw new IllegalStateException();
		}
	}

	@Override
	protected void validate(CollectionLinkElement<T, T> element, boolean transactionEnd) {
		checkOrder(element);
	}

	private void checkOrder(CollectionLinkElement<T, T> element) {
		theHelper.checkOrder(getElements(), element);
	}

	@Override
	public String toString() {
		return "sorted()";
	}

	private static final Map<TestValueType, List<? extends Comparator<?>>> COMPARATORS;

	static {
		COMPARATORS = new HashMap<>();
		for (TestValueType type : TestValueType.values()) {
			switch (type) {
			case INT:
				COMPARATORS.put(type, Arrays.asList(LambdaUtils.printableComparator(Integer::compareTo, () -> "int asc")));
				break;
			case DOUBLE:
				COMPARATORS.put(type, Arrays.asList(LambdaUtils.printableComparator(Double::compareTo, () -> "double asc")));
				break;
			case STRING:
				COMPARATORS.put(type, Arrays.asList(LambdaUtils.printableComparator(String::compareTo, () -> "string asc")));
				break;
			case BOOLEAN:
				COMPARATORS.put(type, Arrays.asList(LambdaUtils.printableComparator(Boolean::compareTo, () -> "boolean asc")));
				break;
			}
		}
	}

	/**
	 * @param type The type to sort values of
	 * @param helper The randomness to get the sorting with
	 * @return A sorting scheme for the given type
	 */
	public static <E> Comparator<E> compare(TestValueType type, TestHelper helper) {
		List<Comparator<E>> typeCompares = (List<Comparator<E>>) COMPARATORS.get(type);
		return typeCompares.get(helper.getInt(0, typeCompares.size()));
	}
}
