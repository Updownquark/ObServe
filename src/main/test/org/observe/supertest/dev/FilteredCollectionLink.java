package org.observe.supertest.dev;

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
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
import org.qommons.TestHelper.RandomAction;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;

public class FilteredCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	private final SimpleSettableValue<Function<E, String>> theFilterValue;
	private final boolean isFilterVariable;
	private Function<E, String> theFilter;

	private final BetterList<E> theSourceValues;
	private final BetterSortedSet<ElementId> thePresentSourceElements;
	private final BetterList<Integer> theNewSourceValues;

	public FilteredCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper, boolean checkRemovedValues, SimpleSettableValue<Function<E, String>> filter, boolean variableFilter) {
		super(parent, type, flow, helper, false, checkRemovedValues);
		theFilterValue = filter;
		isFilterVariable = variableFilter;
		theFilter = filter.get();

		theSourceValues = new BetterTreeList<>(false);
		thePresentSourceElements = new BetterTreeSet<>(false, ElementId::compareTo);
		for (E value : getParent().getCollection()) {
			ElementId srcId = theSourceValues.addElement(value, false).getElementId();
			if (theFilter.apply(value) == null) {
				thePresentSourceElements.add(srcId);
				getExpected().add(value);
			}
		}

		theNewSourceValues = new BetterTreeList<>(false);
		getParent().getCollection().onChange(evt -> {
			switch (evt.getType()) {
			case add:
				theNewSourceValues.add(evt.getIndex());
				break;
			default:
			}
		});
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
				for (int i = 0; i < theSourceValues.size(); i++) {
					CollectionElement<E> srcEl = theSourceValues.getElement(i);
					CollectionElement<ElementId> presentElement = thePresentSourceElements.getElement(srcEl.getElementId(), true); // value
					boolean isIncluded = newFilter.apply(srcEl.get()) == null;
					if (presentElement != null && !isIncluded) {
						int presentIndex = thePresentSourceElements.getElementsBefore(presentElement.getElementId());
						thePresentSourceElements.mutableElement(presentElement.getElementId()).remove();
						ops.add(new CollectionOp<>(CollectionChangeType.remove, srcEl.get(), presentIndex));
					} else if (presentElement == null && isIncluded) {
						presentElement = thePresentSourceElements.addElement(srcEl.getElementId(), true);
						int presentIndex = thePresentSourceElements.getElementsBefore(presentElement.getElementId());
						ops.add(new CollectionOp<>(CollectionChangeType.add, srcEl.get(), presentIndex));
					}
				}
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
					parentOps.add(new CollectionOp<>(op, op.type, op.value, -1));
				break;
			case remove:
				if (op.index >= 0 || theFilter.apply(op.value) == null)
					parentOps.add(new CollectionOp<>(op, op.type, op.value, idxMap.applyAsInt(op.index)));
				break;
			case set:
				msg = theFilter.apply(op.value);
				if (msg != null)
					op.reject(msg, true);
				else
					parentOps.add(new CollectionOp<>(op, op.type, op.value, idxMap.applyAsInt(op.index)));
				break;
			}
		}
		getParent().checkModifiable(parentOps, parentSLS, parentSLE, helper);
	}

	private int getSourceIndex(int index) {
		if (index == thePresentSourceElements.size())
			return theSourceValues.size();
		return theSourceValues.getElementsBefore(thePresentSourceElements.get(index));
	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		List<CollectionOp<E>> filterOps = new ArrayList<>();
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				ElementId srcId = theSourceValues.addElement(op.index, op.value).getElementId();
				if (theFilter.apply(op.value) == null) {
					ElementId presentId = thePresentSourceElements.addElement(srcId, false).getElementId();
					filterOps.add(new CollectionOp<>(op.type, op.value, thePresentSourceElements.getElementsBefore(presentId)));
				}
				break;
			case remove:
				srcId = theSourceValues.getElement(op.index).getElementId();
				CollectionElement<ElementId> presentEl = thePresentSourceElements.getElement(srcId, true); // By value, not by element ID
				if (presentEl != null) {
					filterOps
					.add(new CollectionOp<>(op.type, op.value, thePresentSourceElements.getElementsBefore(presentEl.getElementId())));
					thePresentSourceElements.mutableElement(presentEl.getElementId()).remove();
				}
				theSourceValues.mutableElement(srcId).remove();
				break;
			case set:
				srcId = theSourceValues.getElement(op.index).getElementId();
				theSourceValues.mutableElement(srcId).set(op.value);
				presentEl = thePresentSourceElements.getElement(srcId, true); // By value, not by element ID
				if (presentEl != null) {
					int presentIndex = thePresentSourceElements.getElementsBefore(presentEl.getElementId());
					if (theFilter.apply(op.value) == null)
						filterOps.add(new CollectionOp<>(CollectionChangeType.set, op.value, presentIndex));
					else {
						filterOps.add(new CollectionOp<>(CollectionChangeType.remove, op.value, presentIndex));
						thePresentSourceElements.mutableElement(presentEl.getElementId()).remove();
					}
				} else {
					if (theFilter.apply(op.value) == null) {
						ElementId presentId = thePresentSourceElements.addElement(srcId, false).getElementId();
						filterOps.add(
							new CollectionOp<>(CollectionChangeType.add, op.value, thePresentSourceElements.getElementsBefore(presentId)));
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
			switch (op.type) {
			case add:
				// Assuming that the added elements were added in the same order as the list of operations. Valid?
				int srcIndex = theNewSourceValues.removeFirst();
				parentOps.add(new CollectionOp<>(CollectionChangeType.add, op.value, srcIndex));
				ElementId srcId = theSourceValues.addElement(srcIndex, op.value).getElementId();
				ElementId presentId = thePresentSourceElements.addElement(srcId, false).getElementId();
				Assert.assertEquals(op.index, thePresentSourceElements.getElementsBefore(presentId));
				break;
			case remove:
				srcId = thePresentSourceElements.remove(op.index);
				srcIndex = theSourceValues.getElementsBefore(srcId);
				theSourceValues.mutableElement(srcId).remove();
				parentOps.add(new CollectionOp<>(CollectionChangeType.remove, op.value, srcIndex));
				break;
			case set:
				srcId = thePresentSourceElements.get(op.index);
				srcIndex = theSourceValues.getElementsBefore(srcId);
				theSourceValues.mutableElement(srcId).set(op.value);
				parentOps.add(new CollectionOp<>(CollectionChangeType.set, op.value, srcIndex));
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
	public void check(boolean transComplete) {
		super.check(transComplete);
		theNewSourceValues.clear();
	}

	@Override
	public String toString() {
		String s = "filtered(" + theFilter;
		if (isFilterVariable)
			s += ", variable";
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
				typeFilters.add(filter((Double d) -> d % 1 == 0, "round numbers only"));
				typeFilters.add(filter((Double d) -> d % 1 != 0, "decimal numbers only"));
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
