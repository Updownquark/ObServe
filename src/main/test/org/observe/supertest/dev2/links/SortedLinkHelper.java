package org.observe.supertest.dev2.links;

import java.util.Comparator;

import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableCollectionLink.CollectionOpType;
import org.observe.supertest.dev2.ObservableCollectionLink.OperationRejection;
import org.qommons.BiTuple;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class SortedLinkHelper<T> {
	private final Comparator<? super T> theCompare;

	public SortedLinkHelper(Comparator<? super T> compare) {
		theCompare = compare;
	}

	public BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> expectAdd(T value, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first,
		OperationRejection rejection) {
		if (after != null) {
			int comp = theCompare.compare(value, after.getValue());
			if (comp < 0) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION, true);
				return null;
			} else if (comp != 0)
				after = null; // The source order won't matter
		}
		if (before != null) {
			int comp = theCompare.compare(value, before.getValue());
			if (comp > 0) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION, true);
				return null;
			} else if (comp != 0)
				before = null; // The source order won't matter
		}
		return new BiTuple<>(after, before);
	}

	public BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> expectMove(CollectionLinkElement<?, T> source,
		CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection) {
		T value = source.getValue();
		if (after != null) {
			int comp = theCompare.compare(value, after.getValue());
			if (comp < 0) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION, true);
				return null;
			} else if (comp != 0)
				after = null; // The source order won't matter
		}
		if (before != null) {
			int comp = theCompare.compare(value, before.getValue());
			if (comp > 0) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION, true);
				return null;
			} else if (comp != 0)
				before = null; // The source order won't matter
		}
		return new BiTuple<>(after, before);
	}

	public boolean expectSet(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection,
		BetterList<CollectionLinkElement<T, T>> elements) {
		if (derivedOp.getType() != CollectionOpType.set)
			return true;
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) derivedOp.getElement();
		CollectionElement<CollectionLinkElement<T, T>> adj = elements.getAdjacentElement(element.getElementAddress(), false);
		while (adj != null && !adj.get().isPresent())
			adj = elements.getAdjacentElement(adj.getElementId(), false);
		if (adj != null) {
			int comp = theCompare.compare(derivedOp.getValue(), adj.get().getValue());
			if (comp < 0 || (comp == 0
				&& element.getFirstSource().getElementAddress().compareTo(adj.get().getFirstSource().getElementAddress()) < 0)) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION, true);
				return false;
			}
		}
		adj = elements.getAdjacentElement(element.getElementAddress(), true);
		while (adj != null && !adj.get().isPresent())
			adj = elements.getAdjacentElement(adj.getElementId(), true);
		if (adj != null) {
			int comp = theCompare.compare(derivedOp.getValue(), adj.get().getValue());
			if (comp > 0 || (comp == 0 && !element.getSourceElements().isEmpty()
				&& element.getFirstSource().getElementAddress().compareTo(adj.get().getFirstSource().getElementAddress()) > 0)) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION, true);
				return false;
			}
		}
		return true;
	}

	public boolean expectMoveFromSource(ExpectedCollectionOperation<?, T> sourceOp, int siblingIndex,
		BetterList<CollectionLinkElement<T, T>> elements) {
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(siblingIndex)
			.getFirst();
		boolean expectMove;
		int comp = theCompare.compare(sourceOp.getValue(), element.getValue());
		if (comp < 0) {
			CollectionElement<CollectionLinkElement<T, T>> adj = elements.getAdjacentElement(element.getElementAddress(), false);
			while (adj != null && !adj.get().isPresent())
				adj = elements.getAdjacentElement(adj.getElementId(), false);
			if (adj == null)
				expectMove = false;
			else {
				comp = theCompare.compare(sourceOp.getValue(), adj.get().getValue());
				if (comp < 0)
					expectMove = true;
				else if (comp == 0) {
					comp = element.getSourceElements().getFirst().getElementAddress().compareTo(//
						adj.get().getSourceElements().getFirst().getElementAddress());
					expectMove = comp < 0;
				} else
					expectMove = false;
			}
		} else if (comp == 0) {
			expectMove = false;
		} else {
			CollectionElement<CollectionLinkElement<T, T>> adj = elements.getAdjacentElement(element.getElementAddress(), true);
			while (adj != null && !adj.get().isPresent())
				adj = elements.getAdjacentElement(adj.getElementId(), true);
			if (adj == null)
				expectMove = false;
			else {
				comp = theCompare.compare(sourceOp.getValue(), adj.get().getValue());
				if (comp > 0)
					expectMove = true;
				else if (comp == 0 && !element.getSourceElements().isEmpty()) {
					comp = element.getSourceElements().getFirst().getElementAddress().compareTo(//
						adj.get().getSourceElements().getFirst().getElementAddress());
					expectMove = comp > 0;
				} else
					expectMove = false;
			}
		}
		return expectMove;
	}

	public void checkOrder(BetterList<CollectionLinkElement<T, T>> elements, CollectionLinkElement<T, T> element) {
		if (element.isPresent()) {
			CollectionElement<CollectionLinkElement<T, T>> adj = elements.getAdjacentElement(element.getElementAddress(), false);
			while (adj != null && (!adj.get().isPresent() || adj.get().wasAdded()))
				adj = elements.getAdjacentElement(adj.getElementId(), false);
			if (adj != null) {
				int comp = theCompare.compare(adj.get().getValue(), element.getValue());
				if (comp > 0)
					throw new AssertionError("Sorted elements not in value order");
				else if (comp == 0 && !element.getSourceElements().isEmpty()) {
					comp = adj.get().getSourceElements().getFirst().getElementAddress().compareTo(//
						element.getSourceElements().getFirst().getElementAddress());
					if (comp >= 0)
						throw new AssertionError("Equivalent sorted elements not in source order");
				}
			}
		}
	}
}
