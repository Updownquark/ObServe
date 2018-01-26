package org.observe.supertest.dev;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.qommons.BiTuple;
import org.qommons.TestHelper;
import org.qommons.collect.BetterList;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BinaryTreeNode;

public class SortedCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	private class SortedElement {
		E value;
		final ElementId sourceId;
		final ElementId sortedId;

		SortedElement(E value, ElementId sourceId, ElementId sortedId) {
			this.value = value;
			this.sourceId = sourceId;
			this.sortedId = sortedId;
		}

		@Override
		public String toString() {
			return String.valueOf(value);
		}
	}

	private final Comparator<? super E> theCompare;
	private final Comparator<SortedElement> theElementCompare;

	private final BetterList<SortedElement> theSourceElements;
	private final BetterTreeList<SortedElement> theSortedElements;

	public SortedCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper, boolean checkRemovedValues, Comparator<? super E> compare) {
		super(parent, type, flow, helper, checkRemovedValues);
		theCompare = compare;
		theElementCompare = (el1, el2) -> {
			int comp = theCompare.compare(el1.value, el2.value);
			if (comp == 0)
				comp = el1.sourceId.compareTo(el2.sourceId);
			return comp;
		};

		theSourceElements = new BetterTreeList<>(false);
		theSortedElements = new BetterTreeList<>(false);
	}

	@Override
	public void initialize(TestHelper helper) {
		super.initialize(helper);
		for (E value : getParent().getCollection()) {
			ElementId srcId = theSourceElements.addElement(null, false).getElementId();
			ElementId sortedEl = insert(value, srcId);
			getExpected().add(theSortedElements.getElementsBefore(sortedEl), value);
		}
	}

	private ElementId insert(E value, ElementId srcId) {
		BinaryTreeNode<SortedElement> node = theSortedElements.getRoot();
		if (node == null) {
			ElementId sortedId = theSortedElements.addElement(null, false).getElementId();
			SortedElement element = new SortedElement(value, srcId, sortedId);
			theSourceElements.mutableElement(srcId).set(element);
			theSortedElements.mutableElement(sortedId).set(element);
			return sortedId;
		}
		SortedElement temp = new SortedElement(value, srcId, null);
		int nodeCompare = theElementCompare.compare(node.get(), temp);
		BinaryTreeNode<SortedElement> next = nodeCompare <= 0 ? node.getRight() : node.getLeft();
		while (next != null) {
			node = next;
			nodeCompare = theElementCompare.compare(node.get(), temp);
			next = nodeCompare <= 0 ? node.getRight() : node.getLeft();
		}
		ElementId sortedId = theSortedElements.mutableNodeFor(node).add(null, nodeCompare > 0);
		SortedElement element = new SortedElement(value, srcId, sortedId);
		theSourceElements.mutableElement(srcId).set(element);
		theSortedElements.mutableElement(sortedId).set(element);
		return sortedId;
	}

	private ElementId search(E value) {
		BinaryTreeNode<SortedElement> node = theSortedElements.getRoot();
		if (node == null)
			return null;
		int nodeCompare = theCompare.compare(node.get().value, value);
		BinaryTreeNode<SortedElement> next = nodeCompare < 0 ? node.getRight() : node.getLeft();
		while (next != null) {
			node = next;
			nodeCompare = theCompare.compare(node.get().value, value);
			next = nodeCompare < 0 ? node.getRight() : node.getLeft();
		}

		return nodeCompare == 0 ? node.getElementId() : null;
	}

	private int compareAt(E value, int index) {
		return theCompare.compare(theSortedElements.get(index).value, value);
	}

	@Override
	public void checkModifiable(List<CollectionOp<E>> ops, int subListStart, int subListEnd, TestHelper helper) {
		if (ops.isEmpty())
			return;
		List<CollectionOp<E>> parentOps = new ArrayList<>(ops.size());
		boolean addAll = CollectionOp.isAddAllIndex(ops);
		E preStart, postEnd;
		if (addAll) {
			// addAll(index, values) is implemented as subList(index, index).addAll(values)
			int index = subListStart + ops.get(0).index;
			subListStart = subListEnd = index;
		}
		preStart = subListStart == 0 ? null : theSortedElements.get(subListStart - 1).value;
		postEnd = subListEnd == theSortedElements.size() ? null : theSortedElements.get(subListEnd).value;
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				if (!addAll && op.index >= 0) {
					int idx = op.index + subListStart;
					if (idx > 0 && compareAt(op.value, idx - 1) > 0)
						op.reject(StdMsg.ILLEGAL_ELEMENT, true);
					else if (idx < theSortedElements.size() && compareAt(op.value, idx) < 0)
						op.reject(StdMsg.ILLEGAL_ELEMENT, true);
					else
						parentOps.add(new CollectionOp<>(op, CollectionChangeType.add, op.value, -1));
				} else {
					if (preStart != null && theCompare.compare(preStart, op.value) > 0)
						op.reject(StdMsg.ILLEGAL_ELEMENT_POSITION, true);
					else if (postEnd != null && theCompare.compare(postEnd, op.value) < 0)
						op.reject(StdMsg.ILLEGAL_ELEMENT_POSITION, true);
					else
						parentOps.add(op.index < 0 ? op : new CollectionOp<>(op, op.type, op.value, -1));
				}
				break;
			case remove:
				if (op.index >= 0) {
					int srcIndex = theSourceElements.getElementsBefore(theSortedElements.get(subListStart + op.index).sourceId);
					parentOps.add(new CollectionOp<>(op, CollectionChangeType.remove, op.value, srcIndex));
				} else
					parentOps.add(op);
				break;
			case set:
				int idx = op.index + subListStart;
				SortedElement element = theSortedElements.get(idx);
				if (idx > 0) {
					SortedElement adjElement = theSortedElements.get(idx - 1);
					int comp = theCompare.compare(adjElement.value, op.value);
					if (comp > 0)
						op.reject(StdMsg.ILLEGAL_ELEMENT, true);
					else if (comp == 0 && adjElement.sourceId.compareTo(element.sourceId) > 0) {
						// Replacement would move the element due to source order
						op.reject(StdMsg.ILLEGAL_ELEMENT, true);
					}
				}
				if (idx < theSortedElements.size() - 1) {
					SortedElement adjElement = theSortedElements.get(idx + 1);
					int comp = theCompare.compare(adjElement.value, op.value);
					if (comp < 0)
						op.reject(StdMsg.ILLEGAL_ELEMENT, true);
					else if (comp == 0 && adjElement.sourceId.compareTo(element.sourceId) < 0) {
						// Replacement would move the element due to source order
						op.reject(StdMsg.ILLEGAL_ELEMENT, true);
					}
				}
				if (op.getMessage() == null) {
					int srcIndex = theSourceElements.getElementsBefore(element.sourceId);
					parentOps.add(new CollectionOp<>(op, CollectionChangeType.set, op.value, srcIndex));
				}
				break;
			}
		}
		getParent().checkModifiable(parentOps, 0, theSourceElements.size(), helper);
	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		List<CollectionOp<E>> sortedOps = new ArrayList<>(ops.size());
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				ElementId srcId = theSourceElements.addElement(op.index, null).getElementId();
				ElementId sortedId = insert(op.value, srcId);
				sortedOps
				.add(new CollectionOp<>(CollectionChangeType.add, op.value, theSortedElements.getElementsBefore(sortedId)));
				break;
			case remove:
				SortedElement element = theSourceElements.remove(op.index);
				int sortedIndex = theSortedElements.getElementsBefore(element.sortedId);
				theSortedElements.mutableElement(element.sortedId).remove();
				sortedOps.add(new CollectionOp<>(CollectionChangeType.remove, op.value, sortedIndex));
				break;
			case set:
				element = theSourceElements.get(op.index);
				int sortedIdx = theSortedElements.getElementsBefore(element.sortedId);
				boolean move = (sortedIdx > 0 && compareAt(op.value, sortedIdx - 1) > 0)//
					|| (sortedIdx < theSortedElements.size() - 1 && compareAt(op.value, sortedIdx + 1) < 0);
				if (move) {
					theSortedElements.mutableElement(element.sortedId).remove();
					sortedId = insert(op.value, element.sourceId);
					sortedOps.add(new CollectionOp<>(CollectionChangeType.remove, element.value, sortedIdx));
					sortedIdx = theSortedElements.getElementsBefore(sortedId);
					element.value = op.value;
					sortedOps.add(new CollectionOp<>(CollectionChangeType.add, op.value, sortedIdx));
				} else {
					element.value = op.value;
					sortedOps.add(new CollectionOp<>(CollectionChangeType.set, op.value, sortedIdx));
				}
				break;
			}
		}
		modified(sortedOps, helper, true);
	}

	@Override
	public void fromAbove(List<CollectionOp<E>> ops, TestHelper helper, boolean above) {
		List<CollectionOp<E>> parentOps = new ArrayList<>(ops.size());
		Deque<BiTuple<Integer, E>> newSourceValues = new LinkedList<>(
			((AbstractObservableCollectionLink<?, E>) getParent()).getNewValues());
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				ElementId sortedId = theSortedElements.addElement(op.index, null).getElementId();
				BiTuple<Integer, E> newSource = newSourceValues.removeFirst();
				Assert.assertEquals(newSource.getValue2(), op.value);
				int srcIndex = newSource.getValue1();
				ElementId srcId = theSourceElements.addElement(srcIndex, null).getElementId();
				SortedElement element = new SortedElement(op.value, srcId, sortedId);
				theSortedElements.mutableElement(sortedId).set(element);
				theSourceElements.mutableElement(srcId).set(element);
				parentOps.add(new CollectionOp<>(CollectionChangeType.add, op.value, srcIndex));
				break;
			case remove:
				element = theSortedElements.remove(op.index);
				srcIndex = theSourceElements.getElementsBefore(element.sourceId);
				theSourceElements.mutableElement(element.sourceId).remove();
				parentOps.add(new CollectionOp<>(CollectionChangeType.remove, op.value, srcIndex));
				break;
			case set:
				element = theSortedElements.get(op.index);
				element.value = op.value;
				srcIndex = theSourceElements.getElementsBefore(element.sourceId);
				parentOps.add(new CollectionOp<>(CollectionChangeType.set, op.value, srcIndex));
				break;
			}
		}
		modified(ops, helper, !above);
		getParent().fromAbove(parentOps, helper, true);
	}

	@Override
	public String toString() {
		return "sorted(" + getExtras() + ")";
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
			}
		}
	}

	public static <E> Comparator<E> compare(TestValueType type, TestHelper helper) {
		List<Comparator<E>> typeCompares = (List<Comparator<E>>) COMPARATORS.get(type);
		return typeCompares.get(helper.getInt(0, typeCompares.size()));
	}
}
