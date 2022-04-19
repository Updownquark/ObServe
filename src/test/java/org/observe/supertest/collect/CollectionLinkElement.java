package org.observe.supertest.collect;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Assert;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.supertest.CollectionOpType;
import org.observe.supertest.ObservableChainLink;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionUtils;
import org.qommons.collect.ElementId;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;

/**
 * An element in an {@link ObservableCollectionLink} corresponding to all elements currently in the collection, as well as any elements
 * removed during the currently executing modification.
 *
 * @param <S> The type of the link's source collection
 * @param <T> The type of the link's collection
 */
public class CollectionLinkElement<S, T> implements Comparable<CollectionLinkElement<S, T>> {
	private final ObservableCollectionLink<S, T> theCollectionLink;
	private final BetterSortedSet<CollectionLinkElement<?, S>> theSourceElements;
	private BetterList<CollectionLinkElement<T, ?>>[] theDerivedElements;
	private final ElementId theCollectionAddress;
	private final ElementId theElementAddress;

	private int theLastKnownIndex;

	private boolean wasAdded;
	private boolean wasRemoved;
	private boolean wasUpdated;
	private T theValue;
	private T theCollectionValue;
	private int isAddExpected;
	private boolean isRemoveExpected;

	private List<String> theErrors;

	private Object theCustomData;

	/**
	 * @param collectionLink The link that this element belongs to
	 * @param collectionAddress The ID of this element in the link's one-step collection
	 * @param elementAddress The address of this element in the link's {@link ObservableCollectionLink#getElements() elements}
	 */
	public CollectionLinkElement(ObservableCollectionLink<S, T> collectionLink, ElementId collectionAddress, ElementId elementAddress) {
		theCollectionLink = collectionLink;
		theCollectionAddress = collectionAddress;
		theElementAddress = elementAddress;

		theSourceElements = BetterTreeSet.<CollectionLinkElement<?, S>> buildTreeSet(CollectionLinkElement::compareTo).build();

		theErrors = new LinkedList<>();
		theLastKnownIndex = theCollectionLink.getElements().getElementsBefore(elementAddress);
		wasAdded = true;
		theCollectionValue = getActualCollectionValue();

		if (theCollectionLink.getSourceLink() instanceof ObservableCollectionLink) {
			ObservableCollectionLink<?, S> source = (ObservableCollectionLink<?, S>) theCollectionLink.getSourceLink();
			BetterList<ElementId> sourceElements = theCollectionLink.getCollection().getSourceElements(theCollectionAddress,
				source.getCollection());
			if (sourceElements.isEmpty()) {
				sourceElements = theCollectionLink.getCollection().getSourceElements(theCollectionAddress, // DEBUGGING
					source.getCollection());
				Assert.assertFalse("No source elements", sourceElements.isEmpty());
			}
			int siblingIndex = theCollectionLink.getSiblingIndex();
			for (ElementId sourceEl : sourceElements) {
				CollectionLinkElement<?, S> sourceLinkEl = source.getElement(sourceEl);
				theSourceElements.add(sourceLinkEl);
				sourceLinkEl.addDerived(siblingIndex, this);
			}
		}
	}

	/**
	 * Called when this element is removed from the actual collection
	 *
	 * @param oldValue The previous value of the element in the collection
	 */
	public void removed(T oldValue) {
		if (theCollectionLink.getDef().checkOldValues
			&& !theCollectionLink.getCollection().equivalence().elementEquals(theCollectionValue, oldValue))
			throw new AssertionError(
				theCollectionLink.getPath() + ": Old values do not match: Expected " + theCollectionValue + " but was " + oldValue);
		wasRemoved = true;
	}

	/**
	 * Called when a set operation occurs on this element in the actual collection
	 *
	 * @param oldValue The previous value of the element in the collection
	 * @param newValue The new value of the element in the collection
	 */
	public void updated(T oldValue, T newValue) {
		if (theCollectionLink.getDef().checkOldValues
			&& !theCollectionLink.getCollection().equivalence().elementEquals(theCollectionValue, oldValue))
			throw new AssertionError(
				theCollectionLink.getPath() + ": Old values do not match: Expected " + theCollectionValue + " but was " + oldValue);
		wasUpdated = true;
		theCollectionValue = newValue;
	}

	/** @return The value of this element, as last known by the collection link */
	public T getValue() {
		return theValue;
	}

	/** @return The address of this element in the link's {@link ObservableCollectionLink#getElements() elements} */
	public ElementId getElementAddress() {
		return theElementAddress;
	}

	/** @return The ID of this element in the link's one-step collection */
	public ElementId getCollectionAddress() {
		return theCollectionAddress;
	}

	/** @return All collection elements from the link's source that are sources of this element */
	public BetterList<CollectionLinkElement<?, S>> getSourceElements() {
		return BetterCollections.unmodifiableList(theSourceElements);
	}

	/** @return The first collection element from the link's source that is a source of this element */
	public CollectionLinkElement<?, S> getFirstSource() {
		return theSourceElements.getFirst();
	}

	/**
	 * @param siblingIndex The {@link ObservableChainLink#getSiblingIndex() sibling index} of the derived link to get derived elements for
	 * @return All elements in the given derived collection link that have this element as a source
	 */
	public BetterList<CollectionLinkElement<T, ?>> getDerivedElements(int siblingIndex) {
		if (theDerivedElements == null)
			return BetterList.empty();
		else if (siblingIndex >= theDerivedElements.length)
			return BetterList.empty();
		BetterList<CollectionLinkElement<T, ?>> derived = theDerivedElements[siblingIndex];
		return derived == null ? BetterList.empty() : BetterCollections.unmodifiableList(derived);
	}

	void addDerived(int siblingIndex, CollectionLinkElement<T, ?> derived) {
		// Due to initialization order and various things, the derived elements must be lazily initialized and maintained
		if (theDerivedElements == null) {
			theDerivedElements = new BetterList[Math.max(siblingIndex + 1, theCollectionLink.getDerivedLinks().size())];
		} else if (siblingIndex >= theDerivedElements.length) {
			BetterList<CollectionLinkElement<T, ?>>[] newDerived = new BetterList[Math.max(siblingIndex + 1,
				theCollectionLink.getDerivedLinks().size())];
			System.arraycopy(theDerivedElements, 0, newDerived, 0, theDerivedElements.length);
			theDerivedElements = newDerived;
		}
		if (theDerivedElements[siblingIndex] == null)
			theDerivedElements[siblingIndex] = BetterTreeList.<CollectionLinkElement<T, ?>> build().build();
		theDerivedElements[siblingIndex].add(derived);
	}

	/**
	 * @return The current value of the element as reported by the collection's {@link ObservableCollection#onChange(Consumer) onChange}
	 *         method
	 */
	public T getCollectionValue() {
		return theCollectionValue;
	}

	/** @return The current value of the element in the actual collection */
	public T getActualCollectionValue() {
		return theCollectionLink.getCollection().getElement(theCollectionAddress).get();
	}

	/** @return Whether the element is currently present in the actual collection */
	public boolean isPresent() {
		return theCollectionAddress.isPresent();
	}

	/** @return The index of this element in the link's elements */
	public int getIndex() {
		if (theElementAddress.isPresent())
			return theCollectionLink.getElements().getElementsBefore(theElementAddress);
		else
			return theLastKnownIndex;
	}

	/**
	 * @param customData The custom data to store for this element
	 * @return This element
	 */
	public CollectionLinkElement<S, T> withCustomData(Object customData) {
		theCustomData = customData;
		return this;
	}

	/**
	 * @param <V> The presumed type of the data
	 * @return The custom data stored for this element
	 */
	public <V> V getCustomData() {
		return (V) theCustomData;
	}

	/** @return Whether this element was added and has not yet been {@link #expectAdded(Object) expected} */
	public boolean wasAdded() {
		return wasAdded;
	}

	/** @return Whether this element was updated ({@link CollectionChangeType#set}) during the current modification */
	public boolean wasUpdated() {
		return wasUpdated;
	}

	/** @return Whether this element's addition has been expected */
	public boolean isAddExpected() {
		return isAddExpected > 0;
	}

	/** @return Whether this element's removal is expected */
	public boolean isRemoveExpected() {
		return isRemoveExpected;
	}

	@Override
	public int compareTo(CollectionLinkElement<S, T> o) {
		return theElementAddress.compareTo(o.theElementAddress);
	}

	/**
	 * Logs an error message with this element, to be thrown when its link next {@link ObservableChainLink#validate(boolean) validates}
	 *
	 * @param err Appends an error to log
	 * @return This element
	 */
	public CollectionLinkElement<S, T> error(Consumer<StringBuilder> err) {
		StringBuilder str = new StringBuilder().append('[').append(getIndex()).append(']');
		if (theCollectionAddress.isPresent())
			str.append(getCollectionValue());
		else
			str.append(theValue);
		str.append(": ");
		err.accept(str);
		theErrors.add(str.toString());
		return this;
	}

	/**
	 * Logs an error message with this element, to be thrown when its link next {@link ObservableChainLink#validate(boolean) validates}
	 *
	 * @param err The error to log
	 * @return This element
	 */
	public CollectionLinkElement<S, T> error(String err) {
		return error(e -> e.append(err));
	}

	/**
	 * Marks this element as an expected new element in the collection as a result of the current modification
	 *
	 * @param value The expected initial value of the element
	 * @return This element
	 */
	public CollectionLinkElement<S, T> expectAdded(T value) {
		if (isRemoveExpected && !wasRemoved)
			isRemoveExpected = false;
		else
			isAddExpected++;
		theValue = value;
		if (!theCollectionLink.getDerivedLinks().isEmpty()) {
			ExpectedCollectionOperation<S, T> op = new ExpectedCollectionOperation<>(this, CollectionOpType.add, null, value);
			for (CollectionSourcedLink<T, ?> derived : theCollectionLink.getDerivedLinks())
				derived.expectFromSource(op);
		}
		return this;
	}

	/**
	 * Marks this element as expected to be removed from the collection as a result of the current modification
	 *
	 * @return This element
	 */
	public CollectionLinkElement<S, T> expectRemoval() {
		isRemoveExpected = true;
		if (!theCollectionLink.getDerivedLinks().isEmpty()) {
			ExpectedCollectionOperation<S, T> op = new ExpectedCollectionOperation<>(this, CollectionOpType.remove, theValue, theValue);
			for (CollectionSourcedLink<T, ?> derived : theCollectionLink.getDerivedLinks())
				derived.expectFromSource(op);
		}
		return this;
	}

	/** @param value The new value to expect in element */
	public void expectSet(T value) {
		T oldValue = theValue;
		theValue = value;
		if (!theCollectionLink.getDerivedLinks().isEmpty()) {
			ExpectedCollectionOperation<S, T> op = new ExpectedCollectionOperation<>(this, CollectionOpType.set, oldValue, theValue);
			for (CollectionSourcedLink<T, ?> derived : theCollectionLink.getDerivedLinks())
				derived.expectFromSource(op);
		}
	}

	/**
	 * Updates this element's source elements
	 *
	 * @param withRemove Whether to remove source elements that are no longer in the source collection, or just add new ones
	 */
	public void updateSourceLinks(boolean withRemove) {
		if (!wasRemoved && theCollectionLink.getSourceLink() instanceof ObservableCollectionLink) {
			ObservableCollectionLink<?, S> source = (ObservableCollectionLink<?, S>) theCollectionLink.getSourceLink();
			BetterList<ElementId> sourceElements = theCollectionLink.getCollection().getSourceElements(theCollectionAddress,
				source.getCollection());
			if (sourceElements.isEmpty()) {
				sourceElements = theCollectionLink.getCollection().getSourceElements(theCollectionAddress, // DEBUGGING
					source.getCollection());
				Assert.assertFalse("No source elements", sourceElements.isEmpty());
			}
			int siblingIndex = theCollectionLink.getSiblingIndex();
			CollectionUtils.synchronize(theSourceElements, sourceElements, (e1, e2) -> e1.getCollectionAddress().equals(e2))//
			.simple(sourceEl -> {
				CollectionLinkElement<?, S> sourceLinkEl = source.getElement(sourceEl);
				if (sourceLinkEl == null)
					throw new IllegalStateException("No such link found: " + sourceEl);
				return sourceLinkEl;
			}).withRemove(withRemove).commonUses(true, false).addLast()//
			.onLeft(sourceEl -> {
				if (withRemove)
					sourceEl.getLeftValue().theDerivedElements[siblingIndex].remove(CollectionLinkElement.this);
			}).onRight(sourceEl -> {
				CollectionLinkElement<?, S> sourceLinkEl = source.getElement(sourceEl.getRightValue());
				if (sourceLinkEl == null)
					throw new IllegalStateException("No such link found: " + sourceEl);
				sourceLinkEl.addDerived(siblingIndex, CollectionLinkElement.this);
			})//
			.adjust();

			if (getSourceElements().isEmpty())
				error("No source elements--should have been removed");
		}
	}

	/**
	 * Checks this element for any inconsistencies or errors
	 *
	 * @param error The string builder to print any errors into
	 */
	public void validate(StringBuilder error) {
		ObservableCollection<T> collection = theCollectionLink.getCollection();
		int index = theCollectionLink.getElements().getElementsBefore(theElementAddress);
		if (isAddExpected > 0) {
			if (!wasAdded) {
				if (isAddExpected == 1)
					error("Mistakenly expected re-addition");
				else
					error("Mistakenly expected re-addition " + isAddExpected + " times");
			} else if (isAddExpected > 1) {
				error("Expected addition too many times: " + isAddExpected);
			}
		} else if (wasAdded)
			error("Unexpected addition of " + theCollectionValue);
		if (wasRemoved != isRemoveExpected) {
			if (isRemoveExpected)
				error("Expected removal");
			else
				error("Unexpected removal");
		} else if (isRemoveExpected) { // Removed as expected
			theCollectionLink.getUnprotectedElements().mutableElement(theElementAddress).remove();
			int siblingIndex = theCollectionLink.getSiblingIndex();
			for (CollectionLinkElement<?, S> sourceLink : theSourceElements) {
				if (sourceLink.getElementAddress().isPresent())
					sourceLink.theDerivedElements[siblingIndex].remove(this);
			}
		} else if (!collection.equivalence().elementEquals(getCollectionValue(), theValue)) {
			if (wasUpdated)
				error(err -> err.append("Unexpected update from ").append(theValue));
			else
				error(err -> err.append("Expected update to ").append(theCollectionValue));
		}
		if (!theErrors.isEmpty()) {
			for (String err : theErrors)
				error.append(err).append('\n');
			theErrors.clear();
		}
		if (theElementAddress.isPresent()) {
			theLastKnownIndex = index;
			if (theCollectionAddress.isPresent())
				theValue = getCollectionValue();
		}
		wasAdded = wasUpdated = false;
		isAddExpected = 0;
		updateSourceLinks(true);
	}

	/**
	 * Prints this collection element and its sources, their sources, etc.
	 *
	 * @param str The string builder to append to (one will be created if null)
	 * @param indent The amount to indent
	 * @return The string builder
	 */
	public StringBuilder printAncestry(StringBuilder str, int indent) {
		if (str == null)
			str = new StringBuilder();
		for (int i = 0; i < indent; i++)
			str.append('\t');
		str.append(theCollectionLink.getPath()).append(':').append(theCollectionLink).append(' ').append(toString());
		if (theCustomData instanceof CollectionLinkElement)
			((CollectionLinkElement<?, ?>) theCustomData).printAncestry(str.append('\n'), indent + 1);
		else if (theCustomData != null)
			str.append(" (").append(theCustomData).append(')');
		for (CollectionLinkElement<?, S> sourceEl : theSourceElements)
			sourceEl.printAncestry(str.append('\n'), indent + 1);
		return str;
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		if (wasAdded)
			str.append("new:").append(theCollectionValue);
		else {
			if (wasRemoved)
				str.append("(-)");
			str.append(theValue);
		}
		str.append('@').append(getIndex());
		return str.toString();
	}
}
