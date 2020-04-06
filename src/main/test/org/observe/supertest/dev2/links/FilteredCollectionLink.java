package org.observe.supertest.dev2.links;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.SettableValue;
import org.observe.SimpleSettableValue;
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

import com.google.common.reflect.TypeToken;

public class FilteredCollectionLink<T> extends ObservableCollectionLink<T, T> {
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
			SettableValue<Function<T, String>> filterValue = new SimpleSettableValue<>(
				(TypeToken<Function<T, String>>) (TypeToken<?>) new TypeToken<Object>() {}, false);
			filterValue.set(FilteredCollectionLink.filterFor(sourceCL.getDef().type, helper), null);
			boolean variableFilter = helper.getBoolean();
			CollectionDataFlow<?, ?, T> derivedOneStepFlow = sourceCL.getCollection().flow();
			CollectionDataFlow<?, ?, T> derivedMultiStepFlow = sourceCL.getDef().multiStepFlow;
			if (variableFilter) { // The refresh has to be UNDER the filter
				derivedOneStepFlow = derivedOneStepFlow.refresh(filterValue.changes().noInit());
				derivedMultiStepFlow = derivedMultiStepFlow.refresh(filterValue.changes().noInit());
			}
			derivedOneStepFlow = derivedOneStepFlow.filter(v -> filterValue.get().apply(v));
			derivedMultiStepFlow = derivedMultiStepFlow.filter(v -> filterValue.get().apply(v));
			ObservableCollectionTestDef<T> def = new ObservableCollectionTestDef<>(sourceCL.getType(), derivedOneStepFlow,
				derivedMultiStepFlow, true, true);
			return (ObservableCollectionLink<T, X>) new FilteredCollectionLink<>(sourceCL, def, filterValue, variableFilter, helper);
		}
	};

	private final SettableValue<Function<T, String>> theFilterValue;
	private final boolean isVariableFilter;

	public FilteredCollectionLink(ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def,
		SettableValue<Function<T, String>> filter, boolean variable, TestHelper helper) {
		super(sourceLink, def, helper);
		theFilterValue = filter;
		isVariableFilter = variable;
	}

	@Override
	public boolean isAcceptable(T value) {
		String msg = theFilterValue.get().apply(value);
		if (msg != null)
			return false;
		return getSourceLink().isAcceptable(value);
	}

	@Override
	public T getUpdateValue(T value) {
		return getSourceLink().getUpdateValue(value);
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, int derivedIndex) {
		String msg = theFilterValue.get().apply(value);
		if (msg != null) {
			rejection.reject(msg, true);
			return null;
		}
		CollectionLinkElement<?, T> sourceAdded = getSourceLink().expectAdd(value, //
			after == null ? null : (CollectionLinkElement<?, T>) after.getSourceElements().getFirst(), //
				before == null ? null : (CollectionLinkElement<?, T>) before.getSourceElements().getFirst(), //
					first, rejection, getSiblingIndex());
		if (rejection.isRejected())
			return null;
		CollectionLinkElement<T, T> added = (CollectionLinkElement<T, T>) sourceAdded.getDerivedElements(getSiblingIndex()).getFirst();
		added.expectAdded(value);

		int d = 0;
		for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks()) {
			if (d != derivedIndex)
				derivedLink.expectFromSource(//
					new ExpectedCollectionOperation<>(added, CollectionChangeType.add, null, added.getCollectionValue()));
			d++;
		}
		return added;
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, int derivedIndex) {
		switch (derivedOp.getType()) {
		case add:
			throw new IllegalStateException();
		case remove:
			break;
		case set:
			String msg = theFilterValue.get().apply(derivedOp.getValue());
			if (msg != null) {
				rejection.reject(msg, true);
				return;
			}
			break;
		}
		getSourceLink()
		.expect(new ExpectedCollectionOperation<>((CollectionLinkElement<?, T>) derivedOp.getElement().getSourceElements().getFirst(),
			derivedOp.getType(), derivedOp.getOldValue(), derivedOp.getValue()), rejection, getSiblingIndex());
		if (rejection.isRejected())
			return;

		switch (derivedOp.getType()) {
		case add:
			throw new IllegalStateException();
		case remove:
			derivedOp.getElement().expectRemoval();
			break;
		case set:
			derivedOp.getElement().setValue(derivedOp.getValue());
			break;
		}
		int d = 0;
		for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks()) {
			if (d != derivedIndex)
				derivedLink.expectFromSource(derivedOp);
			d++;
		}
	}

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {
		CollectionLinkElement<T, T> element;
		ExpectedCollectionOperation<?, T> op;
		switch (sourceOp.getType()) {
		case add:
			String msg = theFilterValue.get().apply(sourceOp.getValue());
			if (msg != null)
				return;
			element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex()).getFirst();
			element.expectAdded(sourceOp.getValue());
			op = new ExpectedCollectionOperation<>(element, sourceOp.getType(), sourceOp.getOldValue(), sourceOp.getValue());
			break;
		case remove:
			msg = theFilterValue.get().apply(sourceOp.getOldValue());
			if (msg != null)
				return;
			element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex()).getFirst();
			element.expectRemoval();
			op = new ExpectedCollectionOperation<>(element, sourceOp.getType(), sourceOp.getOldValue(), sourceOp.getValue());
			break;
		case set:
			String preMsg = theFilterValue.get().apply(sourceOp.getOldValue());
			String postMsg = theFilterValue.get().apply(sourceOp.getValue());
			if (preMsg != null) {
				if (postMsg != null)
					return; // Filtered out both before and after
				// Filtered out before, but now included
				element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex()).getFirst();
				element.expectAdded(sourceOp.getValue());
				op = new ExpectedCollectionOperation<>(element, CollectionChangeType.add, null, sourceOp.getValue());
			} else if (postMsg != null) {
				// Included before, but now filtered out
				element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex()).getFirst();
				element.expectRemoval();
				op = new ExpectedCollectionOperation<>(element, CollectionChangeType.remove, sourceOp.getOldValue(),
					sourceOp.getOldValue());
			} else {
				// Included before and after
				element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex()).getFirst();
				element.setValue(sourceOp.getValue());
				op = new ExpectedCollectionOperation<>(element, sourceOp.getType(), sourceOp.getOldValue(), sourceOp.getValue());
			}
			break;
		default:
			throw new IllegalStateException();
		}

		for (CollectionSourcedLink<T, ?> derived : getDerivedLinks())
			derived.expectFromSource(op);
	}

	@Override
	protected void validate(CollectionLinkElement<T, T> element) {
		if (element.isPresent()) {
			CollectionElement<CollectionLinkElement<T, T>> adj = getElements().getAdjacentElement(element.getElementAddress(), false);
			while (adj != null && !adj.get().isPresent())
				adj = getElements().getAdjacentElement(adj.getElementId(), false);
			if (adj != null) {
				int comp = adj.get().getSourceElements().getFirst().getElementAddress().compareTo(//
					element.getSourceElements().getFirst().getElementAddress());
				if (comp >= 0)
					throw new AssertionError("Filtered elements not in source order");
			}
		}
	}

	@Override
	public String toString() {
		String str = "filter(" + theFilterValue.get();
		if (isVariableFilter)
			str += ", variable";
		return str + ")";
	}

	public static <T> Function<T, String> filterFor(TestValueType type, TestHelper helper) {
		List<Function<?, String>> typeFilters = FILTERS.get(type);
		return (Function<T, String>) typeFilters.get(helper.getInt(0, typeFilters.size()));
	}

	static final Map<TestValueType, List<Function<?, String>>> FILTERS;
	static {
		Map<TestValueType, List<Function<?, String>>> filters = new TreeMap<>();
		for (TestValueType type : TestValueType.values()) {
			List<Function<?, String>> typeFilters = new ArrayList<>();
			filters.put(type, Collections.unmodifiableList(typeFilters));
			switch (type) {
			case INT:
				typeFilters.add(filter((Integer i) -> i > 500, ">500 only"));
				typeFilters.add(filter((Integer i) -> i < 500, "<500 only"));
				typeFilters.add(filter((Integer i) -> i % 3 == 0, "x3 only"));
				typeFilters.add(filter((Integer i) -> i % 2 == 1, "odd only"));
				break;
			case DOUBLE:
				typeFilters.add(filter((Double d) -> d == Math.floor(d), "round numbers only"));
				typeFilters.add(filter((Double d) -> d != Math.floor(d), "decimal numbers only"));
				break;
			case STRING:
				typeFilters.add(filter((String s) -> s.length() <= 4, "length<=4 only"));
				typeFilters.add(filter((String s) -> s.length() == 0 ? false : s.charAt(0) % 2 == 0, "even first char only"));
				break;
			case BOOLEAN:
				typeFilters.add(filter((Boolean b) -> b, "true"));
				typeFilters.add(filter((Boolean b) -> b, "false"));
				break;
			}
		}
		FILTERS = Collections.unmodifiableMap(filters);
	}

	private static <T> Function<?, String> filter(Predicate<T> filter, String message) {
		return new Function<T, String>() {
			@Override
			public String apply(T value) {
				return filter.test(value) ? null : message;
			}

			@Override
			public String toString() {
				return message;
			}
		};
	}
}
