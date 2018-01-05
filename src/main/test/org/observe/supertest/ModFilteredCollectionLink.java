package org.observe.supertest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;

public class ModFilteredCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	private final ObservableCollectionDataFlowImpl.ModFilterer<E> theFilter;

	public ModFilteredCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper, ObservableCollectionDataFlowImpl.ModFilterer<E> filter) {
		super(parent, type, flow, helper, false);
		theFilter = filter;

		for (E src : getParent().getCollection())
			getExpected().add(src);
	}

	@Override
	public void checkAddable(List<CollectionOp<E>> adds, int subListStart, int subListEnd, TestHelper helper) {
		List<CollectionOp<E>> parentAdds = new ArrayList<>(adds.size());
		for (CollectionOp<E> op : adds) {
			if (theFilter.getImmutableMessage() != null)
				op.reject(theFilter.getImmutableMessage(), true);
			else if (theFilter.getAddMessage() != null)
				op.reject(theFilter.getAddMessage(), true);
			else if (theFilter.getAddFilter() != null && theFilter.getAddFilter().apply(op.source) != null)
				op.reject(theFilter.getAddFilter().apply(op.source), true);
			else
				parentAdds.add(op);
		}
		getParent().checkAddable(parentAdds, subListStart, subListEnd, helper);
	}

	@Override
	public void checkRemovable(List<CollectionOp<E>> removes, int subListStart, int subListEnd, TestHelper helper) {
		List<CollectionOp<E>> parentRemoves = new ArrayList<>(removes.size());
		for (CollectionOp<E> op : removes) {
			if (theFilter.getImmutableMessage() != null)
				op.reject(theFilter.getImmutableMessage(), true);
			else if (theFilter.getRemoveMessage() != null)
				op.reject(theFilter.getRemoveMessage(), true);
			else if (theFilter.getRemoveFilter() != null && theFilter.getRemoveFilter().apply(op.source) != null)
				op.reject(theFilter.getRemoveFilter().apply(op.source), true); // Relying on the modification supplying the value
			else
				parentRemoves.add(op);
		}
		getParent().checkRemovable(parentRemoves, subListStart, subListEnd, helper);
	}

	@Override
	public void checkSettable(List<CollectionOp<E>> sets, int subListStart, TestHelper helper) {
		List<CollectionOp<E>> parentSets = new ArrayList<>(sets.size());
		for (CollectionOp<E> op : sets) {
			E oldValue = getExpected().get(subListStart + op.index);
			if (oldValue == op.source) {
				// Updates are treated more leniently, since the content of the collection is not changing
				// Updates can only be prevented explicitly
				if (!theFilter.areUpdatesAllowed() && theFilter.getImmutableMessage() != null)
					op.reject(theFilter.getImmutableMessage(), true);
			} else if (theFilter.getImmutableMessage() != null)
				op.reject(theFilter.getImmutableMessage(), true);
			else if (theFilter.getRemoveMessage() != null)
				op.reject(theFilter.getRemoveMessage(), true);
			else if (theFilter.getAddMessage() != null)
				op.reject(theFilter.getAddMessage(), true);
			else if (theFilter.getRemoveFilter() != null && theFilter.getRemoveFilter().apply(oldValue) != null)
				op.reject(theFilter.getRemoveFilter().apply(oldValue), true);
			else if (theFilter.getAddFilter() != null && theFilter.getAddFilter().apply(op.source) != null)
				op.reject(theFilter.getAddFilter().apply(op.source), true);
			else
				parentSets.add(op);
		}
		getParent().checkSettable(parentSets, subListStart, helper);
	}

	@Override
	public void addedFromBelow(List<CollectionOp<E>> adds, TestHelper helper) {
		added(adds, helper, true);
	}

	@Override
	public void removedFromBelow(int index, TestHelper helper) {
		removed(index, helper, true);
	}

	@Override
	public void setFromBelow(int index, E value, TestHelper helper) {
		set(index, value, helper, true);
	}

	@Override
	public void addedFromAbove(List<CollectionOp<E>> adds, TestHelper helper,
		boolean above) {
		getParent().addedFromAbove(adds, helper, true);
		added(adds, helper, !above);
	}

	@Override
	public void removedFromAbove(int index, E value, TestHelper helper, boolean above) {
		getParent().removedFromAbove(index, value, helper, true);
		removed(index, helper, !above);
	}

	@Override
	public void setFromAbove(int index, E value, TestHelper helper, boolean above) {
		set(index, value, helper, !above);
		getParent().setFromAbove(index, value, helper, true);
	}

	@Override
	public String toString() {
		return "mod-filtered(" + theFilter + ")";
	}

	public static <T> Function<? super T, String> filterFor(TestValueType type, TestHelper helper) {
		List<Function<?, String>> typeFilters = FILTERS.get(type);
		return (Function<? super T, String>) typeFilters.get(helper.getInt(0, typeFilters.size()));
	}

	private static final Map<TestValueType, List<Function<?, String>>> FILTERS;
	static {
		Map<TestValueType, List<Function<?, String>>> filters = new TreeMap<>();
		for (TestValueType type : TestValueType.values()) {
			List<Function<?, String>> typeFilters = new ArrayList<>();
			filters.put(type, Collections.unmodifiableList(typeFilters));
			switch (type) {
			case INT:
				typeFilters.add(filter((Integer i) -> i > 500, ">500 only"));
				typeFilters.add(filter((Integer i) -> i > 500, "<500 only"));
				typeFilters.add(filter((Integer i) -> i % 3 != 0, "x3 only"));
				typeFilters.add(filter((Integer i) -> i % 2 == 0, "odd only"));
				break;
			case DOUBLE:
				typeFilters.add(filter((Double d) -> d % 1 != 0, "round numbers only"));
				typeFilters.add(filter((Double d) -> d % 1 == 0, "decimal numbers only"));
				break;
			case STRING:
				typeFilters.add(filter((String s) -> s.length() > 4, "length<=4 only"));
				typeFilters.add(filter((String s) -> s.hashCode() % 5 == 1, "hashCode!=1 only"));
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
