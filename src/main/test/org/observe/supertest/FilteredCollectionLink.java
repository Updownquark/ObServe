package org.observe.supertest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.Assert;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;

public class FilteredCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	private final Function<? super E, String> theFilter;

	private final BetterList<E> theSourceValues;
	private final BetterSortedSet<ElementId> thePresentSourceElements;

	private final BetterSortedSet<ElementId> theNewSourceValues;

	public FilteredCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper, Function<? super E, String> filter) {
		super(parent, type, flow, helper, false);
		theFilter = filter;

		theSourceValues = new BetterTreeList<>(false);
		thePresentSourceElements = new BetterTreeSet<>(false, ElementId::compareTo);
		for (E value : getParent().getCollection()) {
			ElementId srcId = theSourceValues.addElement(value, false).getElementId();
			if (theFilter.apply(value) == null) {
				thePresentSourceElements.add(srcId);
				getExpected().add(value);
			}
		}

		theNewSourceValues = new BetterTreeSet<>(false, ElementId::compareTo);
		getParent().getCollection().onChange(evt -> {
			switch (evt.getType()) {
			case add:
				theNewSourceValues.add(evt.getElementId());
				break;
			default:
			}
		});
	}

	@Override
	public void checkAddable(List<CollectionOp<E>> adds, int subListStart, int subListEnd, TestHelper helper) {
		List<CollectionOp<E>> parentAdds = new ArrayList<>();
		for (CollectionOp<E> op : adds) {
			String msg = theFilter.apply(op.source);
			if (msg != null)
				op.reject(msg, true);
			else
				parentAdds.add(op);
		}
		getParent().checkAddable(parentAdds, getParentSubListStart(subListStart), getParentSubListEnd(subListEnd), helper);
	}

	private int getParentSubListStart(int subListStart) {
		if (subListStart == 0)
			return 0; // Easy
		ElementId srcId = thePresentSourceElements.get(subListStart - 1);
		return theSourceValues.getElementsBefore(srcId) + 1;
	}

	private int getParentSubListEnd(int subListEnd) {
		int parentSubListEnd;
		if (subListEnd == thePresentSourceElements.size())
			parentSubListEnd = theSourceValues.size();
		else {
			ElementId srcId = thePresentSourceElements.get(subListEnd);
			return theSourceValues.getElementsBefore(srcId);
		}
		return parentSubListEnd;
	}

	@Override
	public void checkRemovable(List<CollectionOp<E>> removes, int subListStart, int subListEnd, TestHelper helper) {
		getParent().checkRemovable(removes, getParentSubListStart(subListStart), getParentSubListEnd(subListEnd), helper);
	}

	@Override
	public void checkSettable(List<CollectionOp<E>> sets, int subListStart, TestHelper helper) {
		List<CollectionOp<E>> parentSets = new ArrayList<>();
		for (CollectionOp<E> op : sets) {
			String msg = theFilter.apply(op.source);
			if (msg != null)
				op.reject(msg, true);
			else
				parentSets.add(op);
		}
		getParent().checkSettable(parentSets, getParentSubListStart(subListStart), helper);
	}

	@Override
	public void addedFromBelow(List<CollectionOp<E>> adds, TestHelper helper) {
		List<CollectionOp<E>> filterAdds = new ArrayList<>();
		for (CollectionOp<E> op : adds) {
			ElementId srcId = theSourceValues.addElement(op.index, op.source).getElementId();
			if (theFilter.apply(op.source) == null) {
				ElementId presentId = thePresentSourceElements.addElement(srcId, false).getElementId();
				filterAdds.add(new CollectionOp<>(null, op.source, thePresentSourceElements.getElementsBefore(presentId)));
			}
		}
		added(filterAdds, helper, true);
	}

	@Override
	public void removedFromBelow(int index, TestHelper helper) {
		ElementId srcId = theSourceValues.getElement(index).getElementId();
		CollectionElement<ElementId> presentEl = thePresentSourceElements.getElement(srcId, true); // By value, not by element ID
		if (presentEl != null) {
			removed(thePresentSourceElements.getElementsBefore(presentEl.getElementId()), helper, true);
			thePresentSourceElements.mutableElement(presentEl.getElementId()).remove();
		}
		theSourceValues.mutableElement(srcId).remove();
	}

	@Override
	public void setFromBelow(int index, E value, TestHelper helper) {
		ElementId srcId = theSourceValues.getElement(index).getElementId();
		theSourceValues.mutableElement(srcId).set(value);
		CollectionElement<ElementId> presentEl = thePresentSourceElements.getElement(srcId, true); // By value, not by element ID
		if (presentEl != null) {
			int presentIndex = thePresentSourceElements.getElementsBefore(presentEl.getElementId());
			if (theFilter.apply(value) == null)
				set(presentIndex, value, helper, true);
			else {
				removed(presentIndex, helper, true);
				thePresentSourceElements.mutableElement(presentEl.getElementId()).remove();
			}
		} else {
			if (theFilter.apply(value) == null) {
				ElementId presentId = thePresentSourceElements.addElement(srcId, false).getElementId();
				added(Arrays.asList(new CollectionOp<>(null, value, thePresentSourceElements.getElementsBefore(presentId))), helper, true);
			}
		}
	}

	@Override
	public void addedFromAbove(List<CollectionOp<E>> adds, TestHelper helper, boolean above) {
		List<CollectionOp<E>> parentAdds = new ArrayList<>();
		for (CollectionOp<E> op : adds) {
			int subListStart = getParentSubListStart(op.index);
			int subListEnd = getParentSubListEnd(op.index);
			Iterator<ElementId> nsvIter = theNewSourceValues.iterator();
			boolean found = false;
			int passed = 0;
			int srcIndex = 0;
			while (!found) {
				CollectionElement<E> el = getParent().getCollection().getElement(nsvIter.next());
				if (getCollection().equivalence().elementEquals(el.get(), op.source)) {
					srcIndex = getParent().getCollection().getElementsBefore(el.getElementId()) - passed;
					if (srcIndex >= subListStart && srcIndex <= subListEnd)
						found = true;
				}
				if (found)
					nsvIter.remove();
				else
					passed++;
			}
			parentAdds.add(new CollectionOp<>(null, op.source, srcIndex));
			ElementId srcId = theSourceValues.addElement(srcIndex, op.source).getElementId();
			ElementId presentId = thePresentSourceElements.addElement(srcId, false).getElementId();
			Assert.assertEquals(op.index, thePresentSourceElements.getElementsBefore(presentId));
		}
		added(adds, helper, !above);
		getParent().addedFromAbove(parentAdds, helper, true);
	}

	@Override
	public void removedFromAbove(int index, E value, TestHelper helper, boolean above) {
		ElementId srcId = thePresentSourceElements.remove(index);
		int srcIndex = theSourceValues.getElementsBefore(srcId);
		theSourceValues.mutableElement(srcId).remove();
		getParent().removedFromAbove(srcIndex, value, helper, true);
		removed(index, helper, !above);
	}

	@Override
	public void setFromAbove(int index, E value, TestHelper helper, boolean above) {
		ElementId srcId = thePresentSourceElements.get(index);
		int srcIndex = theSourceValues.getElementsBefore(srcId);
		theSourceValues.mutableElement(srcId).set(value);
		getParent().setFromAbove(srcIndex, value, helper, true);
		set(index, value, helper, !above);
	}

	@Override
	public void check(boolean transComplete) {
		super.check(transComplete);
		theNewSourceValues.clear();
	}

	@Override
	public String toString() {
		return "filtered(" + theFilter + ")";
	}

	public static <T> Function<T, String> filterFor(TestValueType type, TestHelper helper) {
		List<Function<?, String>> typeFilters = FILTERS.get(type);
		return (Function<T, String>) typeFilters.get(helper.getInt(0, typeFilters.size()));
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
				typeFilters.add(filter((Integer i) -> i < 500, "<500 only"));
				typeFilters.add(filter((Integer i) -> i % 3 == 0, "x3 only"));
				typeFilters.add(filter((Integer i) -> i % 2 == 1, "odd only"));
				break;
			case DOUBLE:
				typeFilters.add(filter((Double d) -> d % 1 == 0, "round numbers only"));
				typeFilters.add(filter((Double d) -> d % 1 != 0, "decimal numbers only"));
				break;
			case STRING:
				typeFilters.add(filter((String s) -> s.length() <= 4, "length<=4 only"));
				typeFilters.add(filter((String s) -> s.hashCode() % 5 != 1, "!hashCode x5"));
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
