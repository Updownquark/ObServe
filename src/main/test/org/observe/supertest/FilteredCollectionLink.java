package org.observe.supertest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

import org.junit.Assert;
import org.observe.SimpleSettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
import org.qommons.TestHelper.RandomAction;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.tree.BetterTreeMap;
import org.qommons.tree.BetterTreeSet;

public class FilteredCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	private final SimpleSettableValue<Function<E, String>> theFilterValue;
	private final boolean isFilterVariable;
	private Function<E, String> theFilter;

	private final BetterSortedMap<LinkElement, E> theSourceValues;
	private final BetterSortedSet<ElementId> thePresentSourceElements;

	public FilteredCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper, boolean checkRemovedValues, SimpleSettableValue<Function<E, String>> filter, boolean variableFilter) {
		super(parent, type, flow, helper, checkRemovedValues);
		theFilterValue = filter;
		isFilterVariable = variableFilter;
		theFilter = filter.get();

		theSourceValues = new BetterTreeMap<>(false, LinkElement::compareTo);
		thePresentSourceElements = new BetterTreeSet<>(false, ElementId::compareTo);
	}

	@Override
	public void initialize(TestHelper helper) {
		super.initialize(helper);
		for (int i = 0; i < getParent().getElements().size(); i++) {
			E value = getParent().getCollection().get(i);
			LinkElement srcEl = getParent().getElements().get(i);
			ElementId srcId = theSourceValues.putEntry(srcEl, value, false).getElementId();
			if (theFilter.apply(value) == null) {
				mapSourceElement(srcEl, getElements().get(thePresentSourceElements.size()));
				thePresentSourceElements.add(srcId);
				getExpected().add(value);
			}
		}
	}

	@Override
	protected void addExtraActions(RandomAction action) {
		super.addExtraActions(action);
		if (isFilterVariable) {
			action.or(1, () -> {
				Function<E, String> newFilter = filterFor(getTestType(), action.getHelper());
				Function<E, String> oldFilter = theFilter;
				if (action.getHelper().isReproducing())
					System.out.println("Filter change from " + oldFilter + " to " + newFilter);
				theFilter = newFilter;
				theFilterValue.set(theFilter, null);
				List<CollectionOp<E>> ops = new ArrayList<>();
				theSourceValues.entrySet().spliterator().forEachElement(el -> {
					CollectionElement<ElementId> presentElement = thePresentSourceElements.getElement(el.getElementId(), true); // value
					LinkElement srcLinkEl = el.get().getKey();
					E value = el.get().getValue();
					boolean isIncluded = newFilter.apply(value) == null;
					if (presentElement != null && !isIncluded) {
						int presentIndex = thePresentSourceElements.getElementsBefore(presentElement.getElementId());
						thePresentSourceElements.mutableElement(presentElement.getElementId()).remove();
						LinkElement destLinkEl = getDestElements(srcLinkEl).getLast();
						mapSourceElement(srcLinkEl, destLinkEl);
						ops.add(new CollectionOp<>(CollectionChangeType.remove, destLinkEl, presentIndex, value));
					} else if (presentElement == null && isIncluded) {
						presentElement = thePresentSourceElements.addElement(el.getElementId(), true);
						int presentIndex = thePresentSourceElements.getElementsBefore(presentElement.getElementId());
						LinkElement destLinkEl = getElements().get(presentIndex);
						mapSourceElement(srcLinkEl, destLinkEl);
						ops.add(new CollectionOp<>(CollectionChangeType.add, destLinkEl, presentIndex, value));
					}
				}, true);
				modified(ops, action.getHelper(), true);
			});
		}
	}

	@Override
	public void checkModifiable(List<CollectionOp<E>> ops, int subListStart, int subListEnd, TestHelper helper) {
		if (ops.isEmpty())
			return;
		List<CollectionOp<E>> parentOps = new ArrayList<>(ops.size());
		int parentSLS, parentSLE;
		if (CollectionOp.isAddAllIndex(ops)) {
			parentSLS = getParentSubListStart(subListStart + ops.get(0).index);
			parentSLE = getParentSubListEnd(subListStart + ops.get(0).index);
		} else {
			parentSLS = getParentSubListStart(subListStart);
			parentSLE = getParentSubListEnd(subListEnd);
		}
		IntUnaryOperator idxMap = idx -> {
			if (idx < 0)
				return idx;
			return getSourceIndex(subListStart + idx) - parentSLS;
		};
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				String msg = theFilter.apply(op.value);
				if (msg != null)
					op.reject(msg, true);
				else
					parentOps.add(new CollectionOp<>(op, op.type, -1, op.value));
				break;
			case remove:
				if (op.index >= 0 || theFilter.apply(op.value) == null)
					parentOps.add(new CollectionOp<>(op, op.type, idxMap.applyAsInt(op.index), op.value));
				break;
			case set:
				msg = theFilter.apply(op.value);
				if (msg != null)
					op.reject(msg, true);
				else
					parentOps.add(new CollectionOp<>(op, op.type, idxMap.applyAsInt(op.index), op.value));
				break;
			}
		}
		getParent().checkModifiable(parentOps, parentSLS, parentSLE, helper);
	}

	private int getSourceIndex(int index) {
		if (index == thePresentSourceElements.size())
			return theSourceValues.size();
		return theSourceValues.keySet().getElementsBefore(thePresentSourceElements.get(index));
	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		List<CollectionOp<E>> filterOps = new ArrayList<>();
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				ElementId srcId = theSourceValues.putEntry(op.elementId, op.value, false).getElementId();
				Assert.assertEquals(op.index, theSourceValues.keySet().getElementsBefore(srcId));
				if (theFilter.apply(op.value) == null) {
					LinkElement element = getDestElements(op.elementId).getLast();
					ElementId presentId = thePresentSourceElements.addElement(srcId, false).getElementId();
					filterOps.add(new CollectionOp<>(op.type, element, thePresentSourceElements.getElementsBefore(presentId), op.value));
				}
				break;
			case remove:
				srcId = theSourceValues.getEntry(op.elementId).getElementId();
				Assert.assertEquals(op.index, theSourceValues.keySet().getElementsBefore(srcId));
				CollectionElement<ElementId> presentEl = thePresentSourceElements.getElement(srcId, true); // By value, not by element ID
				if (presentEl != null) {
					LinkElement element = getDestElements(op.elementId).getLast();
					filterOps
					.add(new CollectionOp<>(op.type, element, thePresentSourceElements.getElementsBefore(presentEl.getElementId()),
						op.value));
					thePresentSourceElements.mutableElement(presentEl.getElementId()).remove();
				}
				theSourceValues.mutableEntry(srcId).remove();
				break;
			case set:
				srcId = theSourceValues.getEntry(op.elementId).getElementId();
				Assert.assertEquals(op.index, theSourceValues.keySet().getElementsBefore(srcId));
				E oldValue = theSourceValues.getEntryById(srcId).get();
				theSourceValues.mutableEntry(srcId).set(op.value);
				presentEl = thePresentSourceElements.getElement(srcId, true); // By value, not by element ID
				if (presentEl != null) {
					LinkElement element = getDestElements(op.elementId).getFirst();
					int presentIndex = thePresentSourceElements.getElementsBefore(presentEl.getElementId());
					if (theFilter.apply(op.value) == null) {
						filterOps.add(new CollectionOp<>(CollectionChangeType.set, element, presentIndex, op.value));
					} else {
						filterOps.add(new CollectionOp<>(CollectionChangeType.remove, element, presentIndex, oldValue));
						thePresentSourceElements.mutableElement(presentEl.getElementId()).remove();
					}
				} else {
					if (theFilter.apply(op.value) == null) {
						LinkElement element = getDestElements(op.elementId).getLast();
						ElementId presentId = thePresentSourceElements.addElement(srcId, false).getElementId();
						filterOps.add(new CollectionOp<>(CollectionChangeType.add, element,
							thePresentSourceElements.getElementsBefore(presentId), op.value));
					}
				}
			}
		}
		modified(filterOps, helper, true);
	}

	@Override
	public void fromAbove(List<CollectionOp<E>> ops, TestHelper helper, boolean above) {
		List<CollectionOp<E>> parentOps = new ArrayList<>();
		for (CollectionOp<E> op : ops) {
			LinkElement srcElement = getSourceElement(op.elementId);
			switch (op.type) {
			case add:
				// Assuming that the added elements were added in the same order as the list of operations. Valid?
				ElementId srcId = theSourceValues.putEntry(srcElement, op.value, false).getElementId();
				int srcIndex = theSourceValues.keySet().getElementsBefore(srcId);
				parentOps.add(new CollectionOp<>(CollectionChangeType.add, srcElement, srcIndex, op.value));
				ElementId presentId = thePresentSourceElements.addElement(srcId, false).getElementId();
				Assert.assertEquals(op.index, thePresentSourceElements.getElementsBefore(presentId));
				break;
			case remove:
				srcId = thePresentSourceElements.remove(op.index);
				srcIndex = theSourceValues.keySet().getElementsBefore(srcId);
				theSourceValues.mutableEntry(srcId).remove();
				parentOps.add(new CollectionOp<>(CollectionChangeType.remove, srcElement, srcIndex, op.value));
				break;
			case set:
				srcId = thePresentSourceElements.get(op.index);
				srcIndex = theSourceValues.keySet().getElementsBefore(srcId);
				theSourceValues.mutableEntry(srcId).set(op.value);
				parentOps.add(new CollectionOp<>(CollectionChangeType.set, srcElement, srcIndex, op.value));
				break;
			}
		}
		modified(ops, helper, !above);
		getParent().fromAbove(parentOps, helper, true);
	}

	private int getParentSubListStart(int subListStart) {
		if (thePresentSourceElements.isEmpty())
			return 0;
		if (subListStart == 0)
			return 0; // Easy
		else
			return Math.min(theSourceValues.size(), getSourceIndex(subListStart - 1) + 1);
	}

	private int getParentSubListEnd(int subListEnd) {
		if (thePresentSourceElements.isEmpty())
			return theSourceValues.size();
		if (subListEnd == thePresentSourceElements.size())
			return theSourceValues.size();
		else
			return getSourceIndex(subListEnd);
	}

	@Override
	public String toString() {
		String s = "filtered(" + theFilter;
		if (isFilterVariable)
			s += ", variable";
		s += getExtras();
		s += ")";
		return s;
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
				typeFilters.add(filter((Double d) -> d == Math.floor(d), "round numbers only"));
				typeFilters.add(filter((Double d) -> d != Math.floor(d), "decimal numbers only"));
				break;
			case STRING:
				typeFilters.add(filter((String s) -> s.length() <= 4, "length<=4 only"));
				typeFilters.add(filter((String s) -> s.length() == 0 ? false : s.charAt(0) % 2 == 0, "even first char only"));
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
