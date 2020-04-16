package org.observe.supertest.links;

import java.util.Comparator;

import org.observe.supertest.CollectionLinkElement;
import org.observe.supertest.ExpectedCollectionOperation;
import org.observe.supertest.ObservableCollectionLink;
import org.observe.supertest.OperationRejection;
import org.qommons.BiTuple;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;

/**
 * A helper class for {@link ObservableCollectionLink} whose collections should be sorted by value
 *
 * @param <T> The type of values in the collection
 */
public class SortedLinkHelper<T> {
	private final Comparator<? super T> theCompare;
	private final boolean isUsingFirstSource;

	/**
	 * @param compare The sorting used by the collection
	 * @param useFirstSource Whether the collection should be using the first source element associated with each value as the active
	 *        element
	 */
	public SortedLinkHelper(Comparator<? super T> compare, boolean useFirstSource) {
		theCompare = compare;
		isUsingFirstSource = useFirstSource;
	}

	/** @return The sorting used by the collection */
	public Comparator<? super T> getCompare() {
		return theCompare;
	}

	/** @return Whether the collection should be using the first source element associated with each value as the active element */
	public boolean isUsingFirstSource() {
		return isUsingFirstSource;
	}

	/**
	 * Called when an add attempt is made on the link or downstream of it
	 *
	 * @param value The value to add
	 * @param after The element to add the value after (if any)
	 * @param before The element to add the value before (if any)
	 * @param first Whether to attempt to add the value toward the beginning of the specified range
	 * @param rejection The rejection capability for the operation
	 * @return null if the operation is prohibited by the collection sorting, or a tuple of elements to replace <code>after</code> and
	 *         <code>before</code>
	 */
	public BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> expectAdd(T value, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection) {
		if (after != null) {
			int comp = theCompare.compare(value, after.getValue());
			if (comp < 0) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION);
				return null;
			} else if (comp != 0)
				after = null; // The source order won't matter
		}
		if (before != null) {
			int comp = theCompare.compare(value, before.getValue());
			if (comp > 0) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION);
				return null;
			} else if (comp != 0)
				before = null; // The source order won't matter
		}
		return new BiTuple<>(after, before);
	}

	/**
	 * Called when a move attempt is made on the link or downstream of it
	 *
	 * @param source The element to move
	 * @param after The element to move the element after (if any)
	 * @param before The element to move the element before (if any)
	 * @param first Whether to attempt to move the element toward the beginning of the specified range
	 * @param rejection The rejection capability for the operation
	 * @return null if the operation is prohibited by the collection sorting, or a tuple of elements to replace <code>after</code> and
	 *         <code>before</code>
	 */
	public BiTuple<CollectionLinkElement<?, T>, CollectionLinkElement<?, T>> expectMove(CollectionLinkElement<?, T> source,
		CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection) {
		T value = source.getValue();
		if (after != null) {
			int comp = theCompare.compare(value, after.getValue());
			if (comp < 0) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION);
				return null;
			} else if (comp != 0)
				after = null; // The source order won't matter
		}
		if (before != null) {
			int comp = theCompare.compare(value, before.getValue());
			if (comp > 0) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION);
				return null;
			} else if (comp != 0)
				before = null; // The source order won't matter
		}
		return new BiTuple<>(after, before);
	}

	/**
	 * Called when a non-add, non-move operation is attempted on the link or downstream of it
	 *
	 * @param derivedOp The operation attempt
	 * @param rejection The rejection capability
	 * @param elements The link's elements
	 * @return Whether the operation is accepable as far as the element's sorting is concerned
	 */
	public boolean expectSet(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection,
		BetterList<CollectionLinkElement<T, T>> elements) {
		if (derivedOp.getType() != ExpectedCollectionOperation.CollectionOpType.set)
			return true;
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) derivedOp.getElement();
		CollectionElement<CollectionLinkElement<T, T>> adj = elements.getAdjacentElement(element.getElementAddress(), false);
		while (adj != null && (adj.get().isRemoveExpected() || adj.get().wasAdded()))
			adj = elements.getAdjacentElement(adj.getElementId(), false);
		if (adj != null) {
			int comp = theCompare.compare(derivedOp.getValue(), adj.get().getValue());
			if (comp < 0 || (comp == 0 && isUsingFirstSource
				&& element.getFirstSource().getElementAddress().compareTo(adj.get().getFirstSource().getElementAddress()) < 0)) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION);
				return false;
			}
		}
		adj = elements.getAdjacentElement(element.getElementAddress(), true);
		while (adj != null && (adj.get().isRemoveExpected() || adj.get().wasAdded()))
			adj = elements.getAdjacentElement(adj.getElementId(), true);
		if (adj != null) {
			int comp = theCompare.compare(derivedOp.getValue(), adj.get().getValue());
			if (comp > 0 || (comp == 0 && isUsingFirstSource
				&& element.getFirstSource().getElementAddress().compareTo(adj.get().getFirstSource().getElementAddress()) > 0)) {
				rejection.reject(StdMsg.ILLEGAL_ELEMENT_POSITION);
				return false;
			}
		}
		return true;
	}

	/**
	 * Called for set operations from upstream of the link
	 *
	 * @param sourceOp The source operation
	 * @param siblingIndex The index of the link in it's source's derived elements
	 * @param elements The link's elements
	 * @return Whether the operation must take the form of removing the element and re-adding it in another position
	 */
	public boolean expectMoveFromSource(ExpectedCollectionOperation<?, T> sourceOp, int siblingIndex,
		BetterList<CollectionLinkElement<T, T>> elements) {
		CollectionLinkElement<T, T> element = (CollectionLinkElement<T, T>) sourceOp.getElement().getDerivedElements(siblingIndex)
			.getFirst();
		boolean expectMove;
		int comp = theCompare.compare(sourceOp.getValue(), sourceOp.getOldValue());
		if (comp < 0) {
			CollectionElement<CollectionLinkElement<T, T>> adj = elements.getAdjacentElement(element.getElementAddress(), false);
			while (adj != null && (adj.get().isRemoveExpected() || adj.get().wasAdded()))
				adj = elements.getAdjacentElement(adj.getElementId(), false);
			if (adj == null)
				expectMove = false;
			else {
				comp = theCompare.compare(sourceOp.getValue(), adj.get().getValue());
				if (comp < 0)
					expectMove = true;
				else if (comp == 0 && isUsingFirstSource) {
					comp = element.getFirstSource().getElementAddress().compareTo(//
						adj.get().getFirstSource().getElementAddress());
					expectMove = comp < 0;
				} else
					expectMove = false;
			}
		} else if (comp == 0) {
			expectMove = false;
		} else {
			CollectionElement<CollectionLinkElement<T, T>> adj = elements.getAdjacentElement(element.getElementAddress(), true);
			while (adj != null && (adj.get().isRemoveExpected() || adj.get().wasAdded()))
				adj = elements.getAdjacentElement(adj.getElementId(), true);
			if (adj == null)
				expectMove = false;
			else {
				comp = theCompare.compare(sourceOp.getValue(), adj.get().getValue());
				if (comp > 0)
					expectMove = true;
				else if (comp == 0 && isUsingFirstSource) {
					comp = element.getFirstSource().getElementAddress().compareTo(//
						adj.get().getFirstSource().getElementAddress());
					expectMove = comp > 0;
				} else
					expectMove = false;
			}
		}
		return expectMove;
	}

	/**
	 * @param elements All the link's elements
	 * @param element The element to check the order of
	 */
	public void checkOrder(BetterList<CollectionLinkElement<T, T>> elements, CollectionLinkElement<T, T> element) {
		if (!element.isRemoveExpected() && !element.wasAdded()) {
			if (element.getValue() == null) {
				element.error("Null value");
				return;
			}
			CollectionElement<CollectionLinkElement<T, T>> adj = elements.getAdjacentElement(element.getElementAddress(), false);
			while (adj != null && (adj.get().isRemoveExpected() || adj.get().wasAdded()))
				adj = elements.getAdjacentElement(adj.getElementId(), false);
			if (adj != null) {
				int comp = theCompare.compare(adj.get().getValue(), element.getValue());
				if (comp > 0) {
					element.error("Sorted elements not in value order");
					return;
				} else if (comp == 0 && isUsingFirstSource) {
					comp = adj.get().getFirstSource().getElementAddress().compareTo(//
						element.getFirstSource().getElementAddress());
					if (comp >= 0)
						element.error("Equivalent sorted elements not in source order");
				}
			}
		}
	}
}
