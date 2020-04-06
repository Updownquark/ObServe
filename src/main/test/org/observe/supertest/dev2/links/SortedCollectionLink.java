package org.observe.supertest.dev2.links;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev2.ChainLinkGenerator;
import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.CollectionSourcedLink;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableChainLink;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.TestValueType;
import org.qommons.TestHelper;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class SortedCollectionLink<T> extends ObservableCollectionLink<T, T> {
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink) {
			if (!(sourceLink instanceof ObservableCollectionLink))
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(ObservableChainLink<?, T> sourceLink, TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			Comparator<T> compare = compare(sourceLink.getType(), helper);
			CollectionDataFlow<?, ?, T> derivedOneStepFlow = sourceCL.getCollection().flow();
			CollectionDataFlow<?, ?, T> derivedMultiStepFlow = sourceCL.getDef().multiStepFlow;
			derivedOneStepFlow = derivedOneStepFlow.sorted(compare);
			derivedMultiStepFlow = derivedMultiStepFlow.sorted(compare);
			ObservableCollectionTestDef<T> def = new ObservableCollectionTestDef<>(sourceCL.getType(), derivedOneStepFlow,
				derivedMultiStepFlow, false, sourceCL.getDef().checkOldValues);
			return (ObservableCollectionLink<T, X>) new SortedCollectionLink<>(sourceCL, def, compare, helper);
		}
	};

	private final Comparator<? super T> theCompare;

	public SortedCollectionLink(ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def,
		Comparator<? super T> compare, TestHelper helper) {
		super(sourceLink, def, helper);
		theCompare = compare;
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
		boolean first, OperationRejection rejection, int derivedIndex) {
		if (after != null && theCompare.compare(value, after.get()) < 0) {
			rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION, true);
			return null;
		} else if (before != null && theCompare.compare(value, before.get()) > 0) {
			rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION, true);
			return null;
		}
		CollectionLinkElement<?, T> sourceEl = getSourceLink().expectAdd(value, //
			after == null ? null : (CollectionLinkElement<?, T>) after.getSourceElements().getFirst(),
				before == null ? null : (CollectionLinkElement<?, T>) before.getSourceElements().getFirst(), //
					first, rejection, getSiblingIndex());
		if (rejection.isRejected())
			return null;
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
		element.expectAdded(value);
		checkOrder(element);
		ExpectedCollectionOperation<T, T> op = new ExpectedCollectionOperation<>(element, CollectionChangeType.add, null, value);
		int d = 0;
		for (CollectionSourcedLink<T, ?> derived : getDerivedLinks()) {
			if (d != derivedIndex)
				derived.expectFromSource(op);
			d++;
		}
		return element;
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, int derivedIndex) {
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) derivedOp.getElement();
		switch (derivedOp.getType()) {
		case add:
		case remove:
			break;
		case set:
			CollectionElement<CollectionLinkElement<T, T>> adj = getElements().getAdjacentElement(element.getElementAddress(), false);
			while (adj != null && !adj.get().isPresent())
				adj = getElements().getAdjacentElement(adj.getElementId(), false);
			if (adj != null && theCompare.compare(derivedOp.getValue(), adj.get().get()) < 0) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION, true);
				return;
			}
			adj = getElements().getAdjacentElement(element.getElementAddress(), true);
			while (adj != null && !adj.get().isPresent())
				adj = getElements().getAdjacentElement(adj.getElementId(), true);
			if (adj != null && theCompare.compare(derivedOp.getValue(), adj.get().get()) > 0) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION, true);
				return;
			}
		}
		CollectionLinkElement<?, T> sourceEl = element.getSourceElements().getFirst();
		getSourceLink().expect(new ExpectedCollectionOperation<>(//
			sourceEl, derivedOp.getType(), derivedOp.getOldValue(), derivedOp.getValue()), rejection, getSiblingIndex());
		if (rejection.isRejected())
			return;
		switch (derivedOp.getType()) {
		case add:
			throw new IllegalStateException();
		case remove:
			element.expectRemoval();
			break;
		case set:
			element.setValue(derivedOp.getValue());
			break;
		}
		ExpectedCollectionOperation<T, T> op = new ExpectedCollectionOperation<>(element, derivedOp.getType(), derivedOp.getOldValue(),
			derivedOp.getValue());
		int d = 0;
		for (CollectionSourcedLink<T, ?> derived : getDerivedLinks()) {
			if (d != derivedIndex)
				derived.expectFromSource(op);
			d++;
		}
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
			boolean expectMove;
			int comp = theCompare.compare(sourceOp.getValue(), element.getValue());
			if (comp < 0) {
				CollectionElement<CollectionLinkElement<T, T>> adj = getElements().getAdjacentElement(element.getElementAddress(), false);
				while (adj != null && !adj.get().isPresent())
					adj = getElements().getAdjacentElement(adj.getElementId(), false);
				if (adj == null)
					expectMove = false;
				else {
					comp = theCompare.compare(sourceOp.getValue(), adj.get().get());
					if (comp < 0)
						expectMove = true;
					else if (comp == 0) {
						comp = adj.get().getSourceElements().getFirst().getElementAddress().compareTo(//
							element.getSourceElements().getFirst().getElementAddress());
						expectMove = comp < 0;
					} else
						expectMove = false;
				}
			} else if (comp == 0) {
				expectMove = false;
			} else {
				CollectionElement<CollectionLinkElement<T, T>> adj = getElements().getAdjacentElement(element.getElementAddress(), true);
				while (adj != null && !adj.get().isPresent())
					adj = getElements().getAdjacentElement(adj.getElementId(), true);
				if (adj == null)
					expectMove = false;
				else {
					comp = theCompare.compare(sourceOp.getValue(), adj.get().get());
					if (comp > 0)
						expectMove = true;
					else if (comp == 0) {
						comp = adj.get().getSourceElements().getFirst().getElementAddress().compareTo(//
							element.getSourceElements().getFirst().getElementAddress());
						expectMove = comp > 0;
					} else
						expectMove = false;
				}
			}
			if (expectMove) {
				element.expectRemoval();
				ExpectedCollectionOperation<T, T> op = new ExpectedCollectionOperation<>(element, CollectionChangeType.remove,
					sourceOp.getOldValue(), sourceOp.getOldValue());
				for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
					derived.expectFromSource(op);
				element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex()).get(1);
				element.expectAdded(sourceOp.getValue());
				op = new ExpectedCollectionOperation<>(element, CollectionChangeType.add, null, sourceOp.getValue());
				for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
					derived.expectFromSource(op);
				return;
			} else
				element.setValue(sourceOp.getValue());
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
		if (element.isPresent()) {
			CollectionElement<CollectionLinkElement<T, T>> adj = getElements().getAdjacentElement(element.getElementAddress(), false);
			while (adj != null && (!adj.get().isPresent() || adj.get().wasAdded()))
				adj = getElements().getAdjacentElement(adj.getElementId(), false);
			if (adj != null) {
				int comp = theCompare.compare(adj.get().get(), element.get());
				if (comp > 0)
					throw new AssertionError("Sorted elements not in value order");
				else if (comp == 0) {
					comp = adj.get().getSourceElements().getFirst().getElementAddress().compareTo(//
						element.getSourceElements().getFirst().getElementAddress());
					if (comp >= 0)
						throw new AssertionError("Equivalent sorted elements not in source order");
				}
			}
		}
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
				COMPARATORS.put(type, Arrays.asList((Comparator<Integer>) Integer::compareTo));
				break;
			case DOUBLE:
				COMPARATORS.put(type, Arrays.asList((Comparator<Double>) Double::compareTo));
				break;
			case STRING:
				COMPARATORS.put(type, Arrays.asList((Comparator<String>) String::compareTo));
				break;
			case BOOLEAN:
				COMPARATORS.put(type, Arrays.asList((Comparator<Boolean>) Boolean::compareTo));
				break;
			}
		}
	}

	public static <E> Comparator<E> compare(TestValueType type, TestHelper helper) {
		List<Comparator<E>> typeCompares = (List<Comparator<E>>) COMPARATORS.get(type);
		return typeCompares.get(helper.getInt(0, typeCompares.size()));
	}
}
