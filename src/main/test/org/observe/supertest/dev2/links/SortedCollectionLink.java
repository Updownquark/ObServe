package org.observe.supertest.dev2.links;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev2.ChainLinkGenerator;
import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.CollectionSourcedLink;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableChainLink;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.TestValueType;
import org.qommons.BiTuple;
import org.qommons.LambdaUtils;
import org.qommons.TestHelper;

public class SortedCollectionLink<T> extends ObservableCollectionLink<T, T> {
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

	public SortedCollectionLink(String path, ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def,
		Comparator<? super T> compare, TestHelper helper) {
		super(path, sourceLink, def, helper);
		theHelper = new SortedLinkHelper<>(compare);
	}

	@Override
	public boolean isAcceptable(T value) {
		return getSourceLink().isAcceptable(value);
	}

	@Override
	public T getUpdateValue(T value) {
		return getSourceLink().getUpdateValue(value);
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> afterBefore = theHelper.expectAdd(value, after, before, first,
			rejection);
		if (afterBefore == null)
			return null;
		after = afterBefore.getValue1();
		before = afterBefore.getValue2();

		CollectionLinkElement<?, T> sourceEl = getSourceLink().expectAdd(value, //
			after == null ? null : (CollectionLinkElement<?, T>) after.getSourceElements().getFirst(),
				before == null ? null : (CollectionLinkElement<?, T>) before.getSourceElements().getFirst(), //
					first, rejection);
		if (rejection.isRejected())
			return null;
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
		checkOrder(element);
		return element;
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection) {
		BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> afterBefore = theHelper.expectMove(source, after, before, first,
			rejection);
		if (afterBefore == null)
			return null;
		after = afterBefore.getValue1();
		before = afterBefore.getValue2();
		CollectionLinkElement<?, T> sourceEl = getSourceLink().expectMove(//
			(CollectionLinkElement<?, T>) source.getFirstSource(), //
			after == null ? null : (CollectionLinkElement<?, T>) after.getSourceElements().getFirst(),
				before == null ? null : (CollectionLinkElement<?, T>) before.getSourceElements().getFirst(), //
					first, rejection);
		return sourceEl == null ? null : (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection) {
		if (!theHelper.expectSet(derivedOp, rejection, getElements()))
			return;
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) derivedOp.getElement();
		CollectionLinkElement<?, T> sourceEl = element.getSourceElements().getFirst();
		getSourceLink().expect(new ExpectedCollectionOperation<>(//
			sourceEl, derivedOp.getType(), derivedOp.getOldValue(), derivedOp.getValue()), rejection);
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
			boolean expectMove = theHelper.expectMoveFromSource(sourceOp, getSiblingIndex(), getElements());
			if (expectMove) {
				element.expectRemoval();
				ExpectedCollectionOperation<T, T> op = new ExpectedCollectionOperation<>(element, CollectionOpType.remove,
					sourceOp.getOldValue(), sourceOp.getOldValue());
				for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
					derived.expectFromSource(op);
				element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex()).get(1);
				element.expectAdded(sourceOp.getValue());
				op = new ExpectedCollectionOperation<>(element, CollectionOpType.add, null, sourceOp.getValue());
				for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
					derived.expectFromSource(op);
				return;
			} else
				element.setValue(sourceOp.getValue());
			break;
		case move:
			throw new IllegalStateException();
		}
		ExpectedCollectionOperation<T, T> op = new ExpectedCollectionOperation<>(element, sourceOp.getType(), sourceOp.getOldValue(),
			sourceOp.getValue());
		for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
			derived.expectFromSource(op);
	}

	@Override
	protected void validate(CollectionLinkElement<T, T> element) {
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

	public static <E> Comparator<E> compare(TestValueType type, TestHelper helper) {
		List<Comparator<E>> typeCompares = (List<Comparator<E>>) COMPARATORS.get(type);
		return typeCompares.get(helper.getInt(0, typeCompares.size()));
	}
}
