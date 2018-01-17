package org.observe.supertest.dev;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
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

	private final BetterList<SortedElement> theSourceElements;
	private final BetterTreeList<SortedElement> theSortedElements;
	private final BetterList<Integer> theNewSourceValues;

	public SortedCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper, boolean checkRemovedValues, Comparator<? super E> compare) {
		super(parent, type, flow, helper, false, checkRemovedValues);
		theCompare = compare;

		theSourceElements = new BetterTreeList<>(false);
		theSortedElements = new BetterTreeList<>(false);
		for (E value : getParent().getCollection()) {
			ElementId srcId = theSourceElements.addElement(null, false).getElementId();
			ElementId sortedEl = insert(value, srcId);
			getExpected().add(theSortedElements.getElementsBefore(sortedEl), value);
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

	private ElementId insert(E value, ElementId srcId) {
		BinaryTreeNode<SortedElement> node = theSortedElements.getRoot();
		if (node == null) {
			ElementId sortedId = theSortedElements.addElement(null, false).getElementId();
			SortedElement element = new SortedElement(value, srcId, sortedId);
			theSourceElements.mutableElement(srcId).set(element);
			theSortedElements.mutableElement(sortedId).set(element);
			return sortedId;
		}
		int nodeCompare = theCompare.compare(node.get().value, value);
		BinaryTreeNode<SortedElement> next = nodeCompare <= 0 ? node.getRight() : node.getLeft();
		while (next != null) {
			node = next;
			nodeCompare = theCompare.compare(node.get().value, value);
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
		List<CollectionOp<E>> parentOps = new ArrayList<>(ops.size());
		if (ops.size() > 0 && ops.get(0).type == CollectionChangeType.add && ops.get(0).index >= 0 && isSameIndex(ops)) {
			// addAll(index, values) is implemented as subList(index, index).addAll(values)
			// At the moment, adding into a sub-list is somewhat gimped.
			// The sub list class will only attempt to add the value at the beginning and end of the sub-list
			// So I need to keep a notional running sub list composed of the values that have been addable so far
			// But I only care about the values before and after the beginning and end of the sub list
			int index = subListStart + ops.get(0).index;
			final Optional<E> preSubListStart = index == 0 ? Optional.empty() : Optional.of(theSortedElements.get(index - 1).value);
			final Optional<E> postSubListEnd = index == theSortedElements.size() ? Optional.empty()
				: Optional.of(theSortedElements.get(index).value);
			Optional<E> postSubListStart = postSubListEnd;
			Optional<E> preSubListEnd = preSubListStart;
			for (CollectionOp<E> op : ops) {
				boolean canAddAtStart = true;
				if (preSubListStart.isPresent() && theCompare.compare(preSubListStart.get(), op.value) > 0)
					canAddAtStart = false;
				else if (postSubListStart.isPresent() && theCompare.compare(postSubListStart.get(), op.value) < 0)
					canAddAtStart = false;
				boolean canAddAtEnd = true;
				if (postSubListEnd.isPresent() && theCompare.compare(postSubListEnd.get(), op.value) < 0)
					canAddAtEnd = false;
				else if (preSubListEnd.isPresent() && theCompare.compare(preSubListEnd.get(), op.value) > 0)
					canAddAtEnd = false;
				if (canAddAtStart || canAddAtEnd) {
					parentOps.add(new CollectionOp<>(op, op.type, op.value, -1));
					if (canAddAtStart)
						postSubListStart = Optional.of(op.value);
					if (canAddAtEnd)
						preSubListEnd = Optional.of(op.value);
				} else
					op.reject(StdMsg.ILLEGAL_ELEMENT, true);
			}
		} else {
			for (CollectionOp<E> op : ops) {
				switch (op.type) {
				case add:
					if (op.index >= 0) {
						int idx = op.index + subListStart;
						if (idx > 0 && compareAt(op.value, idx - 1) > 0)
							op.reject(StdMsg.ILLEGAL_ELEMENT, true);
						else if (idx < theSortedElements.size() && compareAt(op.value, idx) < 0)
							op.reject(StdMsg.ILLEGAL_ELEMENT, true);
						else
							parentOps.add(new CollectionOp<>(op, CollectionChangeType.add, op.value, -1));
					} else if (subListStart > 0 || subListEnd < theSortedElements.size()) {
						if (subListStart > 0 && compareAt(op.value, subListStart - 1) > 0)
							op.reject(StdMsg.ILLEGAL_ELEMENT, true);
						else if (subListEnd < theSortedElements.size() && compareAt(op.value, subListEnd) < 0)
							op.reject(StdMsg.ILLEGAL_ELEMENT, true);
						// At the moment, adding into a sub-list is somewhat gimped.
						// The sub list class will only attempt to add the value at the beginning and end of the sub-list
						else if (subListStart < theSortedElements.size() && compareAt(op.value, subListStart) < 0)
							op.reject(StdMsg.ILLEGAL_ELEMENT, true);
						else if (subListEnd > 0 && compareAt(op.value, subListEnd - 1) > 0)
							op.reject(StdMsg.ILLEGAL_ELEMENT, true);
						else
							parentOps.add(op);
					} else
						parentOps.add(op);
					break;
				case remove:
					if (op.index >= 0) {
						int srcIndex = theSourceElements.getElementsBefore(theSortedElements.get(subListStart + op.index).sourceId);
						parentOps.add(new CollectionOp<>(op, CollectionChangeType.remove, op.value, srcIndex));
					} else {
						ElementId found = search(op.value);
						if (found == null)
							op.reject(StdMsg.NOT_FOUND, false);
						else {
							int srcIdx = theSourceElements.getElementsBefore(theSortedElements.getElement(found).get().sourceId);
							parentOps.add(new CollectionOp<>(op, CollectionChangeType.remove, op.value, srcIdx));
						}
					}
					break;
				case set:
					int idx = op.index + subListStart;
					if (idx > 0 && compareAt(op.value, idx - 1) > 0)
						op.reject(StdMsg.ILLEGAL_ELEMENT, true);
					else if (idx < theSortedElements.size() && compareAt(op.value, idx + 1) < 0)
						op.reject(StdMsg.ILLEGAL_ELEMENT, true);
					else {
						int srcIndex = theSourceElements.getElementsBefore(theSortedElements.get(idx).sourceId);
						parentOps.add(new CollectionOp<>(op, CollectionChangeType.set, op.value, srcIndex));
					}
					break;
				}
			}
		}
		getParent().checkModifiable(parentOps, 0, theSourceElements.size(), helper);
	}

	private boolean isSameIndex(List<CollectionOp<E>> ops) {
		int idx = ops.get(0).index;
		for (int i = 1; i < ops.size(); i++)
			if (ops.get(i).index != idx)
				return false;
		return true;
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
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				ElementId sortedId = theSortedElements.addElement(op.index, null).getElementId();
				int sourceIdx = theNewSourceValues.removeFirst();
				ElementId srcId = theSourceElements.addElement(sourceIdx, null).getElementId();
				SortedElement element = new SortedElement(op.value, srcId, sortedId);
				theSortedElements.mutableElement(sortedId).set(element);
				theSourceElements.mutableElement(srcId).set(element);
				parentOps.add(new CollectionOp<>(CollectionChangeType.add, op.value, sourceIdx));
				break;
			case remove:
				element = theSortedElements.remove(op.index);
				sourceIdx = theSourceElements.getElementsBefore(element.sourceId);
				theSourceElements.mutableElement(element.sourceId).remove();
				parentOps.add(new CollectionOp<>(CollectionChangeType.remove, op.value, sourceIdx));
				break;
			case set:
				element = theSortedElements.get(op.index);
				element.value = op.value;
				sourceIdx = theSourceElements.getElementsBefore(element.sourceId);
				parentOps.add(new CollectionOp<>(CollectionChangeType.set, op.value, sourceIdx));
				break;
			}
		}
		modified(ops, helper, !above);
		getParent().fromAbove(parentOps, helper, true);
	}

	@Override
	public void check(boolean transComplete) {
		super.check(transComplete);
		theNewSourceValues.clear();
	}

	@Override
	public String toString() {
		return "sorted";
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
