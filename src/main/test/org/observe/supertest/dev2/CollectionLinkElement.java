package org.observe.supertest.dev2;

import java.util.List;

import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.ElementId;
import org.qommons.tree.BetterTreeSet;

public class CollectionLinkElement<S, T> implements Comparable<CollectionLinkElement<S, T>> {
	private final ObservableCollectionLink<S, T> theCollectionLink;
	private final BetterSortedSet<CollectionLinkElement<?, S>> theSourceElements;
	private final BetterSortedSet<CollectionLinkElement<T, ?>> theDerivedElements;
	private ElementId theElementAddress;
	private ElementId theExpectedAddress;
	private ElementId theCollectionAddress;

	private T theValue;

	public CollectionLinkElement(ObservableCollectionLink<S, T> collectionLink, T value) {
		theCollectionLink = collectionLink;
		theValue = value;

		theSourceElements = new BetterTreeSet<>(false, CollectionLinkElement::compareTo);
		theDerivedElements = new BetterTreeSet<>(false, CollectionLinkElement::compareTo);
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

	public ElementId getExpectedAddress() {
		return theExpectedAddress;
	}

	public ElementId getCollectionAddress() {
		return theCollectionAddress;
	}

	public CollectionLinkElement<S, T> setElementAddress(ElementId address) {
		theElementAddress = address;
		return this;
	}

	public CollectionLinkElement<S, T> setExpectedAddress(ElementId address) {
		theExpectedAddress = address;
		return this;
	}

	public CollectionLinkElement<S, T> setCollectionAddress(ElementId address) {
		theCollectionAddress = address;
		return this;
	}

	public CollectionLinkElement<S, T> withSourceElement(CollectionLinkElement<?, S> source) {
		theSourceElements.add(source);
		return this;
	}

	public CollectionLinkElement<S, T> applyDerivedChanges(List<? extends ExpectedCollectionOperation<T, ?>> derivedChanges) {
		for (ExpectedCollectionOperation<T, ?> change : derivedChanges) {
			switch (change.getType()) {
			case add:
				if (!theDerivedElements.add(change.getElement()))
					throw new IllegalStateException("Derived element already registered: " + change.getElement());
				break;
			case remove:
				if (!theDerivedElements.remove(change.getElement()))
					throw new IllegalStateException("Derived element not found: " + change.getElement());
				break;
			case set:
			}
		}
		return this;
	}

	public BetterList<CollectionLinkElement<?, S>> getSourceElements() {
		return theSourceElements;
	}

	public BetterList<CollectionLinkElement<T, ?>> getDerivedElements() {
		return theDerivedElements;
	}

	public T get() {
		return theValue;
	}

	public int getIndex() {
		return theCollectionLink.getElements().getElementsBefore(theElementAddress);
	}

	@Override
	public int compareTo(CollectionLinkElement<S, T> o) {
		return theExpectedAddress.compareTo(o.theExpectedAddress);
	}

	public void fix(boolean fixSourceElements, boolean fixDerivedElements) {
		if (fixSourceElements)
			theSourceElements.repair(null);
		if (fixDerivedElements)
			theDerivedElements.repair(null);
	}

	public void validateAgainst(CollectionLinkElement<S, T> fromCollection, StringBuilder error) {
		if (!theCollectionLink.getCollection().equivalence().elementEquals(theValue, fromCollection.theValue)) {
			error.append("At [").append(getIndex()).append(", expected ").append(theValue).append(", but was " + fromCollection.theValue)
			.append('\n');
		}
		theValue = fromCollection.theValue;
		theElementAddress = fromCollection.theElementAddress;
		theCollectionAddress = fromCollection.theCollectionAddress;
	}

	@Override
	public String toString() {
		if (theCollectionAddress != null)
			return theValue + "@" + getIndex();
		else
			return String.valueOf(theValue);
	}
}
