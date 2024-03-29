package org.observe.supertest.collect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.SettableValue;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.qommons.LambdaUtils;
import org.qommons.Transactable;
import org.qommons.collect.CollectionElement;
import org.qommons.testing.TestHelper;
import org.qommons.testing.TestHelper.RandomAction;

import com.google.common.reflect.TypeToken;

/**
 * Tests {@link org.observe.collect.ObservableCollection.CollectionDataFlow#filter(Class)}
 *
 * @param <T> The type of the collection values
 */
public class FilteredCollectionLink<T> extends ObservableCollectionLink<T, T> implements CollectionSourcedLink<T, T> {
	/** Generates {@link FilteredCollectionLink}s */
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
			SettableValue<Function<T, String>> filterValue = SettableValue
				.build((TypeToken<Function<T, String>>) (TypeToken<?>) new TypeToken<Object>() {
				}).build();
			filterValue.set(FilteredCollectionLink.filterFor(sourceCL.getDef().type, helper), null);
			boolean variableFilter = helper.getBoolean();
			CollectionDataFlow<?, ?, T> derivedOneStepFlow = sourceCL.getCollection().flow();
			CollectionDataFlow<?, ?, T> derivedMultiStepFlow = sourceCL.getDef().multiStepFlow;
			if (variableFilter) { // The refresh has to be UNDER the filter
				derivedOneStepFlow = derivedOneStepFlow.refresh(filterValue.changes().noInit());
				derivedMultiStepFlow = derivedMultiStepFlow.refresh(filterValue.changes().noInit());
			}
			Function<T, String> filter = LambdaUtils.printableFn(v -> filterValue.get().apply(v), () -> filterValue.get().toString());
			derivedOneStepFlow = derivedOneStepFlow.filter(filter);
			derivedMultiStepFlow = derivedMultiStepFlow.filter(filter);
			ObservableCollectionTestDef<T> def = new ObservableCollectionTestDef<>(sourceCL.getType(), derivedOneStepFlow,
				derivedMultiStepFlow, sourceCL.getDef().orderImportant, sourceCL.getDef().checkOldValues);
			return (ObservableCollectionLink<T, X>) new FilteredCollectionLink<>(path, sourceCL, def, filterValue, variableFilter, helper);
		}
	};

	private final SettableValue<Function<T, String>> theFilterValue;
	private final boolean isVariableFilter;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param def The collection definition for this link
	 * @param filter The filter to apply the source values
	 * @param variable Whether to modify the filter periodically
	 * @param helper The randomness to use to initialize this link
	 */
	public FilteredCollectionLink(String path, ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def,
		SettableValue<Function<T, String>> filter, boolean variable, TestHelper helper) {
		super(path, sourceLink, def, helper);
		theFilterValue = filter;
		isVariableFilter = variable;
	}

	@Override
	public ObservableCollectionLink<?, T> getSourceLink() {
		return (ObservableCollectionLink<?, T>) super.getSourceLink();
	}

	@Override
	protected Transactable getLocking() {
		if (isVariableFilter)
			return Transactable.combine(theFilterValue, super.getLocking());
		else
			return super.getLocking();
	}

	@Override
	public boolean isAcceptable(T value) {
		String msg = theFilterValue.get().apply(value);
		if (msg != null)
			return false;
		return getSourceLink().isAcceptable(value);
	}

	@Override
	public T getUpdateValue(CollectionLinkElement<T, T> element, T value) {
		return ((ObservableCollectionLink<Object, T>) getSourceLink())
			.getUpdateValue((CollectionLinkElement<Object, T>) element.getFirstSource(), value);
	}

	@Override
	public double getModificationAffinity() {
		double affinity = super.getModificationAffinity();
		if (isVariableFilter)
			affinity++;
		return affinity;
	}

	@Override
	public void tryModify(RandomAction action, TestHelper helper) {
		super.tryModify(action, helper);
		if (isVariableFilter) {
			action.or(1, () -> {
				Function<T, String> oldFilter = theFilterValue.get();
				Function<T, String> newFilter = FilteredCollectionLink.filterFor(getType(), helper);
				if (helper.isReproducing())
					System.out.println("Filter " + oldFilter + " -> " + newFilter);
				theFilterValue.set(newFilter, null);
				expectFilterChange(oldFilter, newFilter);
			});
		}
	}

	/**
	 * Called when the filter is switched out
	 *
	 * @param oldFilter The previously used filter
	 * @param newFilter The new filter
	 */
	protected void expectFilterChange(Function<T, String> oldFilter, Function<T, String> newFilter) {
		for (CollectionLinkElement<?, T> sourceEl : getSourceLink().getElements()) {
			String oldMsg = oldFilter.apply(sourceEl.getValue());
			String newMsg = newFilter.apply(sourceEl.getValue());
			if ((oldMsg == null) == (newMsg == null))
				continue;
			if (oldMsg != null) { // Was excluded, now included
				CollectionLinkElement<T, T> linkEl = (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex())
					.getFirst();
				linkEl.expectAdded(sourceEl.getValue());
			} else { // Was included, now excluded
				CollectionLinkElement<T, T> linkEl = (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex())
					.getFirst();
				linkEl.expectRemoval();
			}
		}
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, boolean execute) {
		String msg = theFilterValue.get().apply(value);
		if (msg != null) {
			rejection.reject(msg);
			return null;
		}
		CollectionLinkElement<?, T> sourceAdded = getSourceLink().expectAdd(value, //
			after == null ? null : (CollectionLinkElement<?, T>) after.getFirstSource(), //
				before == null ? null : (CollectionLinkElement<?, T>) before.getFirstSource(), //
					first, rejection, execute);
		if (sourceAdded == null)
			return null;
		return (CollectionLinkElement<T, T>) sourceAdded.getDerivedElements(getSiblingIndex()).getFirst();
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection, boolean execute) {
		CollectionLinkElement<?, T> sourceEl = getSourceLink().expectMove(//
			(CollectionLinkElement<?, T>) source.getFirstSource(), //
			after == null ? null : (CollectionLinkElement<?, T>) after.getFirstSource(), //
				before == null ? null : (CollectionLinkElement<?, T>) before.getFirstSource(), //
					first, rejection, execute);
		return sourceEl == null ? null : (CollectionLinkElement<T, T>) sourceEl.getDerivedElements(getSiblingIndex()).getFirst();
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		switch (derivedOp.getType()) {
		case add:
		case move:
			throw new IllegalStateException();
		case remove:
			break;
		case set:
			String msg = theFilterValue.get().apply(derivedOp.getValue());
			if (msg != null) {
				rejection.reject(msg);
				return;
			}
			break;
		}
		getSourceLink().expect(//
			new ExpectedCollectionOperation<>((CollectionLinkElement<?, T>) derivedOp.getElement().getFirstSource(),
				derivedOp.getType(), derivedOp.getOldValue(), derivedOp.getValue()),
			rejection, execute);
	}

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {
		CollectionLinkElement<T, T> element;
		switch (sourceOp.getType()) {
		case add:
			String msg = theFilterValue.get().apply(sourceOp.getValue());
			if (msg != null)
				return;
			element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex()).getFirst();
			element.expectAdded(sourceOp.getValue());
			break;
		case remove:
			msg = theFilterValue.get().apply(sourceOp.getOldValue());
			if (msg != null)
				return;
			element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex()).getFirst();
			element.expectRemoval();
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
			} else if (postMsg != null) {
				// Included before, but now filtered out
				element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex()).getFirst();
				element.expectRemoval();
			} else {
				// Included before and after
				element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(getSiblingIndex()).getFirst();
				element.expectSet(sourceOp.getValue());
			}
			break;
		default:
			throw new IllegalStateException();
		}
	}

	@Override
	protected void validate(CollectionLinkElement<T, T> element, boolean transactionEnd) {
		if (element.isPresent()) {
			CollectionElement<CollectionLinkElement<T, T>> adj = getElements().getAdjacentElement(element.getElementAddress(), false);
			while (adj != null && !adj.get().isPresent())
				adj = getElements().getAdjacentElement(adj.getElementId(), false);
			if (adj != null) {
				int comp = adj.get().getFirstSource().getElementAddress().compareTo(//
					element.getFirstSource().getElementAddress());
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

	/**
	 * @param <T> The type of value to filter
	 * @param type The test type of value to filter
	 * @param helper The randomness to use to get a random filter
	 * @return A random filter to filter test values of the given type
	 */
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
				typeFilters.add(filter((Integer i) -> Math.abs(i % 3) == 0, "x3 only"));
				typeFilters.add(filter((Integer i) -> Math.abs(i % 2) == 1, "odd only"));
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
				typeFilters.add(filter((Boolean b) -> !b, "false"));
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
