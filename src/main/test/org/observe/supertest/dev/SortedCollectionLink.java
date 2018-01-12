package org.observe.supertest.dev;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

		SortedElement(E value, ElementId sourceId) {
			this.value = value;
			this.sourceId = sourceId;
		}
	}

	private final Comparator<? super E> theCompare;

	private final BetterList<E> theSourceValues;
	private final BetterTreeList<SortedElement> thePresentSourceElements;
	private final BetterList<Integer> theNewSourceValues;

	public SortedCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper, boolean checkRemovedValues, Comparator<? super E> compare) {
		super(parent, type, flow, helper, false, checkRemovedValues);
		theCompare = compare;

		theSourceValues = new BetterTreeList<>(false);
		thePresentSourceElements = new BetterTreeList<>(false);
		for (E value : getParent().getCollection()) {
			ElementId srcId = theSourceValues.addElement(value, false).getElementId();
			ElementId sortedEl = insert(value, srcId);
			getExpected().add(thePresentSourceElements.getElementsBefore(sortedEl), value);
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
		BinaryTreeNode<SortedElement> node = thePresentSourceElements.getRoot();
		if (node == null)
			return thePresentSourceElements.addElement(new SortedElement(value, srcId), false).getElementId();
		int nodeCompare = theCompare.compare(node.get().value, value);
		BinaryTreeNode<SortedElement> next = nodeCompare <= 0 ? node.getLeft() : node.getRight();
		while (next != null) {
			node = next;
			nodeCompare = theCompare.compare(node.get().value, value);
			next = nodeCompare <= 0 ? node.getLeft() : node.getRight();
		}
		return thePresentSourceElements.mutableNodeFor(node).add(new SortedElement(value, srcId), nodeCompare > 0);
	}

	@Override
	public void checkModifiable(List<CollectionOp<E>> ops, int subListStart, int subListEnd, TestHelper helper) {
		List<CollectionOp<E>> parentOps = new ArrayList<>(ops.size());
		for (CollectionOp<E> op : ops) {
			switch (op.type) {
			case add:
				if (op.index > 0) {
					if (op.index > 0 && theCompare.compare(thePresentSourceElements.get(0).value, op.value) > 0)
						op.reject(StdMsg.ILLEGAL_ELEMENT, true);
				}
				break;
			case remove:
				break;
			case set:
				break;
			}
		}
		getParent().checkModifiable(parentOps, 0, theSourceValues.size(), helper);
	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		// TODO Auto-generated method stub

	}

	@Override
	public void fromAbove(List<CollectionOp<E>> ops, TestHelper helper, boolean above) {
		// TODO Auto-generated method stub

	}
}
