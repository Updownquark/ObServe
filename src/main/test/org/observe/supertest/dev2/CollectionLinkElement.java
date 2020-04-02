package org.observe.supertest.dev2;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Assert;
import org.observe.collect.ObservableCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.ElementId;
import org.qommons.tree.BetterTreeSet;

public class CollectionLinkElement<S, T> implements Comparable<CollectionLinkElement<S, T>> {
	private final ObservableCollectionLink<S, T> theCollectionLink;
	private final BetterSortedSet<CollectionLinkElement<?, S>> theSourceElements;
	private final BetterSortedSet<CollectionLinkElement<T, ?>>[] theDerivedElements;
	private final ElementId theCollectionAddress;
	private final ElementId theElementAddress;

	private int theLastKnownIndex;

	private boolean wasAdded;
	private boolean wasRemoved;
	private boolean wasUpdated;
	private T theValue;
	private boolean isRemoveExpected;

	private List<String> theErrors;

	public CollectionLinkElement(ObservableCollectionLink<S, T> collectionLink, ElementId collectionAddress, ElementId elementAddress) {
		theCollectionLink = collectionLink;
		theCollectionAddress = collectionAddress;
		theElementAddress = elementAddress;

		theSourceElements = new BetterTreeSet<>(false, CollectionLinkElement::compareTo);
		theDerivedElements = new BetterSortedSet[collectionLink.getDerivedLinks().size()];
		for (int i = 0; i < theDerivedElements.length; i++)
			theDerivedElements[i] = new BetterTreeSet<>(false, CollectionLinkElement::compareTo);

		theErrors = new LinkedList<>();
		theLastKnownIndex = theCollectionLink.getElements().getElementsBefore(elementAddress);

		if (theCollectionLink.getSourceLink() != null) {
			BetterList<ElementId> sourceElements = theCollectionLink.getCollection().getSourceElements(theCollectionAddress,
				theCollectionLink.getSourceLink().getCollection());
			if (sourceElements.isEmpty()) {
				sourceElements = theCollectionLink.getCollection().getSourceElements(theCollectionAddress,
					theCollectionLink.getSourceLink().getCollection());
				Assert.assertFalse("No source elements", sourceElements.isEmpty());
			}
			int siblingIndex = collectionLink.getSiblingIndex();
			for (ElementId sourceEl : sourceElements) {
				CollectionLinkElement<?, S> sourceLinkEl = theCollectionLink.getSourceLink().getElement(sourceEl);
				theSourceElements.add(sourceLinkEl);
				sourceLinkEl.theDerivedElements[siblingIndex].add(this);
			}
		}

		wasAdded = true;
	}

	public void removed() {
		wasRemoved = true;
	}

	public void updated() {
		wasUpdated = true;
	}

	public T getValue() {
		return theValue;
	}

	public void setValue(T value) {
		theValue = value;
	}

	public ElementId getElementAddress() {
		return theElementAddress;
	}

	public ElementId getCollectionAddress() {
		return theCollectionAddress;
	}

	public CollectionLinkElement<S, T> withSourceElement(CollectionLinkElement<?, S> source) {
		theSourceElements.add(source);
		return this;
	}

	public BetterList<CollectionLinkElement<?, S>> getSourceElements() {
		return theSourceElements;
	}

	public BetterList<CollectionLinkElement<T, ?>> getDerivedElements(int siblingIndex) {
		return theDerivedElements[siblingIndex];
	}

	public T get() {
		return theValue;
	}

	public T getCollectionValue() {
		return theCollectionLink.getCollection().getElement(theCollectionAddress).get();
	}

	public boolean isPresent() {
		return theCollectionAddress.isPresent();
	}

	public int getIndex() {
		return theCollectionLink.getElements().getElementsBefore(theElementAddress);
	}

	public boolean wasAdded() {
		return wasAdded;
	}

	@Override
	public int compareTo(CollectionLinkElement<S, T> o) {
		return theElementAddress.compareTo(o.theElementAddress);
	}

	public CollectionLinkElement<S, T> error(Consumer<StringBuilder> err) {
		StringBuilder str = new StringBuilder().append('[').append(theLastKnownIndex).append(']');
		if (theCollectionAddress.isPresent())
			str.append(getCollectionValue());
		else
			str.append(theValue);
		str.append(": ");
		err.accept(str);
		theErrors.add(str.toString());
		return this;
	}

	public CollectionLinkElement<S, T> error(String err) {
		return error(e -> e.append(err));
	}

	public CollectionLinkElement<S, T> expectAdded(T value) {
		if (isRemoveExpected)
			isRemoveExpected = false;
		else if (wasAdded)
			wasAdded = false;
		else
			error("Mistakenly expected re-addition");
		theValue = value;
		return this;
	}

	public CollectionLinkElement<S, T> expectRemoval() {
		isRemoveExpected = true;
		return this;
	}

	public void validate(StringBuilder error) {
		ObservableCollection<T> collection = theCollectionLink.getCollection();
		if (wasAdded)
			error("Unexpected addition");
		else if (wasRemoved != isRemoveExpected) {
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
				error(err -> err.append("Expected update to ").append(theValue));
		}
		if (!theErrors.isEmpty()) {
			for (String err : theErrors)
				error.append(err).append('\n');
			theErrors.clear();
		}
		if (theElementAddress.isPresent()) {
			theLastKnownIndex = theCollectionLink.getElements().getElementsBefore(theElementAddress);
			theValue = getCollectionValue();
		}
		wasUpdated = false;
	}

	@Override
	public String toString() {
		return theValue + "@" + getIndex();
	}
}
